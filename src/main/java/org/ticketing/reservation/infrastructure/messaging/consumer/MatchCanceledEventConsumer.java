package org.ticketing.reservation.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.ticketing.reservation.application.dto.command.CancelReservationCommand;
import org.ticketing.reservation.application.service.ReservationApplicationService;
import org.ticketing.reservation.domain.exception.InvalidReservationStateException;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.infrastructure.redis.MatchTicketWindowCache;

/**
 * match.canceled 이벤트 소비자.
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>해당 경기의 취소 가능한 예매(PENDING + COMPLETED) ID 목록 조회</li>
 *   <li>각 예매에 대해 {@code cancel()} 호출
 *       <ul>
 *         <li>DB CANCELLED 전이 + Redis 좌석 락 해제</li>
 *         <li>Outbox 트랜잭션 안에서 {@code reservation.canceled} 이벤트 등록
 *             → payment-service 가 환불 처리</li>
 *       </ul>
 *   </li>
 *   <li>{@link MatchTicketWindowCache} 에서 해당 경기 TTL 정보 제거</li>
 * </ol>
 *
 * <h3>부분 실패 처리</h3>
 * <p>개별 예매 취소 실패(일시적 오류)는 격리하여 로그를 남기고 다음 예매로 진행한다.
 * 하나의 실패가 전체 메시지 처리를 막지 않도록 한다.
 * 단, 실패 건이 하나라도 있으면 예외를 전파하여 {@code @RetryableTopic} 재시도를 유도한다.
 * 재시도 시 이미 CANCELLED 된 예매는 {@link InvalidReservationStateException} 으로
 * 멱등 처리되므로 중복 취소가 발생하지 않는다.
 *
 * <h3>재시도 전략</h3>
 * <p>3회(1 + 2) 지수 백오프 재시도. 소진 시 DLT 격리 + ERROR 로그.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchCanceledEventConsumer {

    private final ReservationApplicationService reservationApplicationService;
    private final MatchTicketWindowCache matchTicketWindowCache;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 15000),
            dltTopicSuffix = ".DLT",
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = "${topics.match.canceled:match.canceled}",
            groupId = "${spring.application.name}"
    )
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        MatchCanceledEvent event = objectMapper.readValue(record.value(), MatchCanceledEvent.class);
        UUID matchId = event.matchId();

        log.info("[match.canceled] 수신 — matchId={}, canceledAt={}", matchId, event.canceledAt());

        // 취소 가능한 예매 목록 조회 (PENDING + COMPLETED)
        List<UUID> cancellableIds = reservationApplicationService.findCancellableReservationIds(matchId);

        if (cancellableIds.isEmpty()) {
            log.info("[match.canceled] 취소 대상 예매 없음 — matchId={}", matchId);
            matchTicketWindowCache.evict(matchId);
            return;
        }

        log.info("[match.canceled] 취소 대상 예매 {} 건 — matchId={}", cancellableIds.size(), matchId);

        int successCount = 0;
        int failCount = 0;

        for (UUID reservationId : cancellableIds) {
            try {
                reservationApplicationService.cancel(
                        new CancelReservationCommand(reservationId, "match.canceled"));
                successCount++;

            } catch (InvalidReservationStateException e) {
                // 이미 CANCELLED / EXPIRED → 멱등 처리
                log.info("[match.canceled] 예매 이미 처리됨 (idempotent skip) — "
                        + "reservationId={}, status={}", reservationId, e.getMessage());
                successCount++;

            } catch (ReservationNotFoundException e) {
                // 이미 삭제된 예매 → 무시
                log.warn("[match.canceled] 예매 없음 (skip) — reservationId={}", reservationId);
                successCount++;

            } catch (Exception e) {
                // 일시적 오류 — 개별 격리 후 계속 진행
                log.error("[match.canceled] 예매 취소 실패 — reservationId={}", reservationId, e);
                failCount++;
            }
        }

        log.info("[match.canceled] 처리 완료 — matchId={}, 성공={}, 실패={}",
                matchId, successCount, failCount);

        // MatchTicketWindowCache 정리
        matchTicketWindowCache.evict(matchId);

        // 실패 건이 있으면 재시도 유도 (성공 건은 멱등 처리로 재처리 무해)
        if (failCount > 0) {
            throw new RuntimeException(
                    "[match.canceled] 일부 예매 취소 실패 (" + failCount + "건) — 재시도 대기. matchId=" + matchId);
        }
    }

    /**
     * 최대 재시도 초과 후 DLT 처리.
     *
     * <p>취소되지 않은 예매가 남아 있을 수 있으므로 수동 점검이 필요하다.
     * 결제 완료 예매는 환불이 누락될 수 있어 reconciliation 작업 대상이 된다.
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        try {
            MatchCanceledEvent event = objectMapper.readValue(record.value(), MatchCanceledEvent.class);
            log.error("[match.canceled.DLT] 경기 취소 처리 최대 재시도 초과 — 수동 점검 필요. "
                    + "matchId={}, canceledAt={}", event.matchId(), event.canceledAt());
            // 미취소 예매 → reconciliation 잡 대상
        } catch (Exception e) {
            log.error("[match.canceled.DLT] DLT 처리 중 예외 발생", e);
        }
    }
}

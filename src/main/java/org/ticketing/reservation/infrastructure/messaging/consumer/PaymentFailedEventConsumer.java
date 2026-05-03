package org.ticketing.reservation.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.service.ReservationApplicationService;

/**
 * payment.failed 이벤트 소비자.
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>예매를 EXPIRED 상태로 전이 (DB 커밋)</li>
 *   <li>DB 커밋 후 Redis HOLD 락을 즉시 해제 → 다른 사용자가 해당 좌석을 바로 선점 가능</li>
 * </ol>
 *
 * <h3>좌석 즉시 해제 이유</h3>
 * <p>결제 실패 = 예매 완료 불가. HOLD TTL(15분)까지 기다리면 해당 시간 동안
 * 다른 사용자가 좌석을 선점할 수 없으므로, 결제 실패 수신 즉시 해제한다.
 *
 * <h3>멱등성 처리</h3>
 * <p>{@link ReservationApplicationService#expireIfActive} 를 사용한다.
 * 예매가 없거나 이미 종착 상태(EXPIRED/CANCELLED)이면 서비스 내부에서 skip 처리되므로
 * 소비자가 도메인 예외를 직접 catch 할 필요 없다.
 *
 * <h3>재시도 전략</h3>
 * <p>상태 전이 예외·예매 미존재는 재시도 없이 정상 처리(idempotent).
 * 그 외 일시적 오류(DB 연결 실패 등)는 3회(1 + 2) 지수 백오프 재시도.
 * 재시도 소진 시 DLT로 격리하고 ERROR 로그를 남긴다 — 좌석은 TTL 자연 만료로 해제된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedEventConsumer {

    private final ReservationApplicationService reservationApplicationService;
    private final ObjectMapper objectMapper;

    /**
     * 결제 실패 이벤트 수신 → 예매 만료 및 좌석 즉시 해제.
     *
     * <p>이미 종착 상태(EXPIRED/CANCELLED)이거나 예매를 찾을 수 없는 경우
     * 정상 반환하여 재시도 없이 메시지를 소비한다.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            dltTopicSuffix = ".DLT",
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = "${topics.payment.failed:payment.failed}",
            groupId = "${spring.application.name}"
    )
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        PaymentFailedEvent event = objectMapper.readValue(record.value(), PaymentFailedEvent.class);
        log.info("[payment.failed] 수신 — paymentId={}, orderId={}",
                event.paymentId(), event.orderId());

        // 이미 종착 상태이거나 예매 없음 → 내부에서 skip. 그 외 예외는 전파 → @RetryableTopic 재시도
        reservationApplicationService.expireIfActive(new ExpireReservationCommand(event.orderId()));
        log.info("[payment.failed] 예매 만료 처리 완료 — reservationId={}", event.orderId());
    }

    /**
     * 최대 재시도 초과 후 DLT 처리.
     *
     * <p>일시적 오류가 지속되어 예매 만료에 실패한 경우다.
     * 예매는 HOLD TTL 자연 만료(최대 15분)로 좌석이 해제되므로 별도 보상 이벤트는 발행하지 않는다.
     * 수동 점검을 위해 ERROR 수준으로 기록한다.
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(record.value(), PaymentFailedEvent.class);
            log.error("[payment.failed.DLT] 예매 만료 처리 최대 재시도 초과 — 수동 점검 필요. "
                            + "paymentId={}, orderId={}",
                    event.paymentId(), event.orderId());
            // 좌석은 HOLD TTL 자연 만료로 해제됨 (최대 15분 내)
            // reservation 상태는 PENDING 으로 남아 있으므로 별도 배치/reconciliation 잡 대상
        } catch (Exception e) {
            log.error("[payment.failed.DLT] DLT 처리 중 예외 발생", e);
        }
    }
}

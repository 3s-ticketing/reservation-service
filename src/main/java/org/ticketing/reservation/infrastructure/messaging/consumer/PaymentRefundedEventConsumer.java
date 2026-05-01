package org.ticketing.reservation.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.ticketing.ticket.application.service.TicketService;

/**
 * payment.refunded 이벤트 소비자.
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>예매를 CANCELLED 상태로 전이 + Redis HOLD/RESERVED 락 즉시 해제</li>
 *   <li>해당 예매에 발급된 티켓이 있으면 CANCELED 전이 + 소프트 삭제</li>
 * </ol>
 *
 * <h3>멱등성 처리</h3>
 * <p>{@code @IdempotentConsumer} 를 사용하지 않는다.
 * {@code InboxAdvice} 의 {@code @Transactional} 과 내부 {@code @Transactional}(REQUIRED) 이
 * 같은 트랜잭션을 공유하므로, 상태 전이 실패 예외가 트랜잭션을 rollback-only 로 마킹하여
 * 커밋 시점에 {@code UnexpectedRollbackException} 이 발생하는 문제가 있다.
 *
 * <p>대신 도메인 예외({@link InvalidReservationStateException}, {@link ReservationNotFoundException})를
 * catch 하여 이미 처리된 메시지를 정상 반환으로 처리한다.
 * 티켓 측 멱등성은 {@code TicketService.cancelAndDeleteByReservationId()} 내부에서 처리한다.
 *
 * <h3>재시도 전략</h3>
 * <p>상태 전이 예외·예매 미존재는 재시도 없이 정상 처리(idempotent).
 * 그 외 일시적 오류(DB 연결 실패 등)는 3회(1+2) 지수 백오프 재시도.
 * 재시도 소진 시 DLT 로 격리하고 ERROR 로그를 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundedEventConsumer {

    private final ReservationApplicationService reservationApplicationService;
    private final TicketService ticketService;
    private final ObjectMapper objectMapper;

    /**
     * 결제 환불 이벤트 수신 → 예매 취소 + 티켓 취소·삭제.
     *
     * <p>예매가 이미 CANCELLED 이거나 존재하지 않는 경우 정상 반환한다.
     * 티켓이 없거나 이미 삭제된 경우도 무시한다.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            dltTopicSuffix = ".DLT",
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = "${topics.payment.refunded:payment.refunded}",
            groupId = "${spring.application.name}"
    )
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        PaymentRefundedEvent event = objectMapper.readValue(record.value(), PaymentRefundedEvent.class);
        UUID reservationId = event.orderId();

        log.info("[payment.refunded] 수신 — paymentId={}, reservationId={}",
                event.paymentId(), reservationId);

        // 1. 예매 취소 (DB CANCELLED 전이 + Redis 락 해제)
        try {
            reservationApplicationService.cancel(
                    new CancelReservationCommand(reservationId, "payment.refunded"));
            log.info("[payment.refunded] 예매 취소 완료 — reservationId={}", reservationId);

        } catch (InvalidReservationStateException e) {
            // 이미 CANCELLED / EXPIRED 상태 → 멱등 처리
            log.info("[payment.refunded] 예매가 이미 처리된 상태 (idempotent skip) — "
                    + "reservationId={}, status={}", reservationId, e.getMessage());

        } catch (ReservationNotFoundException e) {
            // 이미 삭제됐거나 존재하지 않는 예매 → 무시
            log.warn("[payment.refunded] 예매를 찾을 수 없음 (skip) — reservationId={}", reservationId);
        }

        // 2. 티켓 취소 + 소프트 삭제 (티켓이 없으면 내부에서 무시)
        ticketService.cancelAndDeleteByReservationId(reservationId, "payment.refunded");
        // 그 외 예외는 전파 → @RetryableTopic 재시도
    }

    /**
     * 최대 재시도 초과 후 DLT 처리.
     *
     * <p>일시적 오류가 지속되어 환불 처리에 실패한 경우다.
     * 예매·티켓 상태가 불일치 상태로 남을 수 있으므로 수동 점검이 필요하다.
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        try {
            PaymentRefundedEvent event = objectMapper.readValue(record.value(), PaymentRefundedEvent.class);
            log.error("[payment.refunded.DLT] 환불 처리 최대 재시도 초과 — 수동 점검 필요. "
                            + "paymentId={}, reservationId={}",
                    event.paymentId(), event.orderId());
        } catch (Exception e) {
            log.error("[payment.refunded.DLT] DLT 처리 중 예외 발생", e);
        }
    }
}

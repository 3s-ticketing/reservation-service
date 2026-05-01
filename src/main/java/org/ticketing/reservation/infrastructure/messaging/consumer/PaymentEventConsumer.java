package org.ticketing.reservation.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.ticketing.common.event.Events;
import org.ticketing.common.messaging.annotation.IdempotentConsumer;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.service.ReservationApplicationService;
import org.ticketing.reservation.domain.event.payload.ReservationConfirmationFailedEvent;

/**
 * payment.completed 이벤트 소비자.
 *
 * <h3>재시도 전략</h3>
 * <p>총 4회(1회 원본 + 3회 재시도) 시도하며, 지수 백오프(2s → 4s → 8s)로 간격을 둔다.
 * 모든 시도가 실패하면 메시지는 {@code payment.completed.DLT} 로 격리되고,
 * {@code reservation.confirmation.failed} 이벤트를 발행하여 payment-service 가 환불을 수행하도록 한다.
 *
 * <h3>멱등성</h3>
 * <p>{@link IdempotentConsumer} AOP 가 Inbox 테이블에 message_id 를 기록한다.
 * 재시도 시 이전 시도의 트랜잭션이 롤백됐으므로 Inbox 레코드도 함께 롤백되어 재처리가 허용된다.
 * 동일 message_id 가 정상 처리된 뒤 재도달하면 중복 처리를 건너뛴다.
 *
 * <h3>트랜잭션</h3>
 * <p>각 시도는 {@code InboxAdvice} 의 {@code @Transactional} 안에서 실행된다.
 * 예외 발생 시 Inbox 저장도 함께 롤백되므로 다음 재시도에서 정상 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ReservationApplicationService reservationApplicationService;
    private final ObjectMapper objectMapper;

    @Value("${topics.reservation.confirmation.failed:reservation.confirmation.failed}")
    private String confirmationFailedTopic;

    /**
     * 결제 완료 이벤트 수신 → 예매 확정 시도.
     *
     * <p>실패 시 Spring Kafka 의 Non-Blocking Retry 로 retry 토픽에 재발행된다.
     * 4회 시도 후에도 실패하면 DLT 로 격리되어 {@link #handleDlt} 가 호출된다.
     */
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            dltTopicSuffix = ".DLT",
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = "${topics.payment.completed:payment.completed}",
            groupId = "${spring.application.name}"
    )
    @IdempotentConsumer("payment.completed")
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        PaymentCompletedEvent event = objectMapper.readValue(record.value(), PaymentCompletedEvent.class);
        log.info("[payment.completed] 수신 — paymentId={}, orderId={}", event.paymentId(), event.orderId());

        UUID reservationId = UUID.fromString(event.orderId());
        reservationApplicationService.confirm(new ConfirmReservationCommand(reservationId));

        log.info("[payment.completed] 예매 확정 완료 — reservationId={}", reservationId);
    }

    /**
     * 최대 재시도 초과 후 DLT 처리.
     *
     * <p>payment-service 가 환불을 수행할 수 있도록
     * {@code reservation.confirmation.failed} 이벤트를 발행한다.
     * 이벤트 발행은 Outbox 패턴을 통해 원자적으로 처리된다.
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(record.value(), PaymentCompletedEvent.class);
            log.error("[payment.completed.DLT] 예매 확정 최대 재시도 초과 — 환불 이벤트 발행. paymentId={}, orderId={}",
                    event.paymentId(), event.orderId());

            UUID reservationId = UUID.fromString(event.orderId());

            Events.trigger(
                    UUID.randomUUID().toString(),
                    "RESERVATION",
                    event.orderId(),
                    confirmationFailedTopic,
                    new ReservationConfirmationFailedEvent(
                            event.paymentId(),
                            reservationId,
                            "예매 확정 최대 재시도 횟수 초과"
                    )
            );
        } catch (Exception e) {
            // DLT 처리 자체가 실패하면 수동 개입 필요 — 로그로 기록
            log.error("[payment.completed.DLT] DLT 처리 중 예외 발생. 수동 개입 필요.", e);
        }
    }
}

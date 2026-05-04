package org.ticketing.reservation.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ticketing.common.event.Events;
import org.ticketing.reservation.domain.event.ReservationEventPublisher;
import org.ticketing.reservation.domain.event.payload.ReservationCancelledEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCompletedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationConfirmationFailedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCreatedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationExpiredEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReservedEvent;

/**
 * Reservation 도메인 이벤트 Kafka 발행 구현체.
 *
 * <p>Outbox 패턴을 통해 DB 트랜잭션과 동일한 원자성 안에서 이벤트를 등록한다.
 * {@link Events#trigger} 호출 시 {@code OutboxEventListener} 가 같은 트랜잭션에
 * Outbox 레코드를 삽입하고, AFTER_COMMIT 이후 릴레이 스케줄러가 Kafka 로 전송한다.
 *
 * <h3>현재 구현 범위</h3>
 * <ul>
 *   <li>{@link #publishCancelled} — 경기 취소·사용자 취소 시 payment-service 환불 트리거</li>
 *   <li>나머지 메서드 — TODO 로그 stub (추후 구현)</li>
 * </ul>
 */
@Slf4j
@Component
public class ReservationEventPublisherImpl implements ReservationEventPublisher {

    private static final String DOMAIN_TYPE = "RESERVATION";

    @Value("${topics.reservation.canceled:reservation.canceled}")
    private String canceledTopic;

    @Value("${topics.reservation.confirmation-failed:reservation.confirmation.failed}")
    private String confirmationFailedTopic;

    @Override
    public void publishCancelled(ReservationCancelledEvent event) {
        Events.trigger(
                "reservation-cancelled-" + event.reservationId(),  // correlationId (멱등성 보장)
                DOMAIN_TYPE,
                event.reservationId().toString(),                   // domainId (Kafka 파티션 키)
                canceledTopic,                                      // eventType = topic
                event                                               // payload
        );
        log.info("[ReservationCancelledEvent] Outbox 등록 - reservationId: {}, reason: {}",
                event.reservationId(), event.cancelReason());
    }

    @Override
    public void publishConfirmationFailed(ReservationConfirmationFailedEvent event) {
        Events.trigger(
                "reservation-confirmation-failed-" + event.reservationId(),
                DOMAIN_TYPE,
                event.reservationId().toString(),
                confirmationFailedTopic,
                event
        );
        log.info("[ReservationConfirmationFailedEvent] Outbox 등록 - reservationId: {}", event.reservationId());
    }

    @Override
    public void publishCreated(ReservationCreatedEvent event) {
        // TODO: 추후 구현
        log.debug("[이벤트] 예매 생성 — reservationId={}", event.reservationId());
    }

    @Override
    public void publishCompleted(ReservationCompletedEvent event) {
        // TODO: 추후 구현
        log.debug("[이벤트] 예매 확정 — reservationId={}", event.reservationId());
    }

    @Override
    public void publishExpired(ReservationExpiredEvent event) {
        // TODO: 추후 구현
        log.debug("[이벤트] 예매 만료 — reservationId={}", event.reservationId());
    }

    @Override
    public void publishSeatHeld(ReservationSeatHeldEvent event) {
        // TODO: 추후 구현
        log.debug("[이벤트] 좌석 HOLD — seatId={}", event.seatId());
    }

    @Override
    public void publishSeatReserved(ReservationSeatReservedEvent event) {
        // TODO: 추후 구현
        log.debug("[이벤트] 좌석 RESERVED — seatId={}", event.seatId());
    }

    @Override
    public void publishSeatReleased(ReservationSeatReleasedEvent event) {
        // TODO: 추후 구현
        log.debug("[이벤트] 좌석 해제 — seatId={}", event.seatId());
    }
}
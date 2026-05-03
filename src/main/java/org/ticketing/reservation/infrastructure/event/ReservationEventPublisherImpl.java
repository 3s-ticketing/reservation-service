package org.ticketing.reservation.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing.common.event.Events;
import org.ticketing.reservation.domain.event.ReservationEventPublisher;
import org.ticketing.reservation.domain.event.payload.ReservationCancelledEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCompletedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCreatedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationExpiredEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReservedEvent;

@Slf4j
@Component
public class ReservationEventPublisherImpl implements ReservationEventPublisher {

    private static final String DOMAIN_TYPE = "RESERVATION";
    private static final String TOPIC_RESERVATION_CANCELLED = "reservation.cancelled";

    @Override
    public void publishCancelled(ReservationCancelledEvent event) {
        Events.trigger(
                "reservation-cancelled-" + event.reservationId(),  // correlationId (멱등성 보장)
                DOMAIN_TYPE,
                event.reservationId().toString(),                   // domainId (Kafka 파티션 키)
                TOPIC_RESERVATION_CANCELLED,                        // eventType = topic
                event                                               // payload
        );
        log.info("[ReservationCancelledEvent] Outbox 등록 - reservationId: {}, reason: {}",
                event.reservationId(), event.cancelReason());
    }

    @Override
    public void publishCreated(ReservationCreatedEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[ReservationCreatedEvent] reservationId: {}", event.reservationId());
    }

    @Override
    public void publishCompleted(ReservationCompletedEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[ReservationCompletedEvent] reservationId: {}", event.reservationId());
    }

    @Override
    public void publishExpired(ReservationExpiredEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[ReservationExpiredEvent] reservationId: {}", event.reservationId());
    }

    @Override
    public void publishSeatHeld(ReservationSeatHeldEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[ReservationSeatHeldEvent] reservationSeatId: {}", event.reservationSeatId());
    }

    @Override
    public void publishSeatReserved(ReservationSeatReservedEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[ReservationSeatReservedEvent] reservationSeatId: {}", event.reservationSeatId());
    }

    @Override
    public void publishSeatReleased(ReservationSeatReleasedEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[ReservationSeatReleasedEvent] reservationSeatId: {}", event.reservationSeatId());
    }
}
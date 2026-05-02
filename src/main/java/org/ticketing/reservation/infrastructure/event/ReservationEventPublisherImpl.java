package org.ticketing.reservation.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
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
@RequiredArgsConstructor
public class ReservationEventPublisherImpl implements ReservationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${topics.reservation.cancelled:reservation.cancelled}")
    private String reservationCancelledTopic;

    @Override
    public void publishCancelled(ReservationCancelledEvent event) {
        kafkaTemplate.send(reservationCancelledTopic, event.reservationId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[ReservationCancelledEvent] 발행 실패 - reservationId: {}, reason: {}",
                                event.reservationId(), event.cancelReason(), ex);
                    } else {
                        log.info("[ReservationCancelledEvent] 발행 성공 - reservationId: {}, reason: {}",
                                event.reservationId(), event.cancelReason());
                    }
                });
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
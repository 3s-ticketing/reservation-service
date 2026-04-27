package org.ticketing.reservation.domain.event;

import org.ticketing.reservation.domain.event.payload.ReservationCancelledEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCompletedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCreatedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationExpiredEvent;

/**
 * Reservation 어그리게이트가 외부 세계로 발행하는 도메인 이벤트의 게이트웨이.
 *
 * <p>도메인 계층은 인터페이스만 알며, Kafka·Outbox 같은 인프라 결정은
 * {@code infrastructure.messaging.kafka.producer.ReservationEventPublisherImpl} 이 담당한다.
 */
public interface ReservationEventPublisher {

    void publishCreated(ReservationCreatedEvent event);

    void publishCompleted(ReservationCompletedEvent event);

    void publishCancelled(ReservationCancelledEvent event);

    void publishExpired(ReservationExpiredEvent event);
}

package org.ticketing.reservation.domain.event;

import org.ticketing.reservation.domain.event.payload.ReservationCancelledEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCompletedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCreatedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationExpiredEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReservedEvent;

/**
 * Reservation 어그리게이트가 외부 세계로 발행하는 도메인 이벤트의 게이트웨이.
 *
 * <p>예매(루트) 라이프사이클 이벤트와 좌석(자식) 라이프사이클 이벤트를 모두 본 어그리게이트가
 * 발행한다. 도메인 계층은 인터페이스만 알며, Kafka·Outbox 같은 인프라 결정은
 * {@code infrastructure.messaging.kafka.producer.ReservationEventPublisherImpl} 이 담당한다.
 */
public interface ReservationEventPublisher {

    // 예매 라이프사이클
    void publishCreated(ReservationCreatedEvent event);

    void publishCompleted(ReservationCompletedEvent event);

    void publishCancelled(ReservationCancelledEvent event);

    void publishExpired(ReservationExpiredEvent event);

    // 좌석 라이프사이클 (Reservation 어그리게이트 내부에서 발생)
    void publishSeatHeld(ReservationSeatHeldEvent event);

    void publishSeatReserved(ReservationSeatReservedEvent event);

    void publishSeatReleased(ReservationSeatReleasedEvent event);
}

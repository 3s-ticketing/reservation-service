package org.ticketing.reservation.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing.reservation.domain.event.ReservationSeatEventPublisher;
import org.ticketing.reservationseat.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservationseat.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservationseat.domain.event.payload.ReservationSeatReservedEvent;

@Slf4j
@Component
public class ReservationSeatEventPublisherImpl implements ReservationSeatEventPublisher {

    @Override
    public void publishHeld(ReservationSeatHeldEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[이벤트] 좌석 HOLD - reservationSeatId={}, seatId={}, matchId={}",
                event.reservationSeatId(), event.seatId(), event.matchId());
    }

    @Override
    public void publishReserved(ReservationSeatReservedEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[이벤트] 좌석 RESERVED - reservationSeatId={}, seatId={}, matchId={}",
                event.reservationSeatId(), event.seatId(), event.matchId());
    }

    @Override
    public void publishReleased(ReservationSeatReleasedEvent event) {
        // TODO: Kafka 연동 시 실제 발행 구현
        log.info("[이벤트] 좌석 해제 - reservationSeatId={}, seatId={}, reason={}",
                event.reservationSeatId(), event.seatId(), event.reason());
    }
}
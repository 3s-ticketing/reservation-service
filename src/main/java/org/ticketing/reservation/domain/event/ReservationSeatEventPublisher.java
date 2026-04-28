package org.ticketing.reservation.domain.event;

import org.ticketing.reservationseat.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservationseat.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservationseat.domain.event.payload.ReservationSeatReservedEvent;

public interface ReservationSeatEventPublisher {

    void publishHeld(ReservationSeatHeldEvent event);

    void publishReserved(ReservationSeatReservedEvent event);

    void publishReleased(ReservationSeatReleasedEvent event);
}

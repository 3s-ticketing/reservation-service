// HoldReservationSeatRequest.java
package org.ticketing.reservation.application.dto.request;

import java.util.UUID;

public record HoldReservationSeatRequest(
        UUID reservationId,
        UUID seatId
) {}
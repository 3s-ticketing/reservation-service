// ConfirmReservationSeatRequest.java
package org.ticketing.reservation.application.dto.request;

import java.util.UUID;

public record ConfirmReservationSeatRequest(
        UUID reservationId,
        UUID seatId
) {}
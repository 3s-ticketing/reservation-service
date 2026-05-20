package org.ticketing.reservation.application.dto.request;

import java.util.UUID;

public record CreateReservationSeatRequest(
        UUID reservationId,
        UUID seatId
) {}
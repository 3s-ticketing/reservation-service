package org.ticketing.reservationseat.application.dto.request;

import java.util.UUID;

public record CreateReservationSeatRequest(
        UUID reservationId,
        UUID seatId
) {}
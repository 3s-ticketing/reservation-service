package org.ticketing.reservationseat.application.dto.command;

import java.util.UUID;

public record CreateReservationSeatCommand(
        UUID userId,
        UUID reservationId,
        UUID seatId
) {}
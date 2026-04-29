package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

public record HoldReservationSeatCommand(
        UUID userId,
        UUID reservationId,
        UUID seatId
) {}

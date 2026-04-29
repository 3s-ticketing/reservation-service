package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

public record ConfirmReservationSeatCommand(
        UUID userId,
        UUID reservationId,
        UUID seatId
) {}
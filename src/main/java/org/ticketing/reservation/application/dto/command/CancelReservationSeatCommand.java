package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

public record CancelReservationSeatCommand(
        UUID userId,
        UUID reservationSeatId
) {}
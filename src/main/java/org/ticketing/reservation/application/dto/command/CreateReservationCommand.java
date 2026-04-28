package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

public record CreateReservationCommand(
        UUID userId,
        UUID matchId,
        Long totalPrice
) {}

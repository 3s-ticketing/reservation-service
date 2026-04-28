package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

public record CancelReservationCommand(
        UUID reservationId,
        String canceledBy
) {}

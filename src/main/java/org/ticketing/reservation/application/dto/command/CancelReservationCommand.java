package org.ticketing.reservation.application.dto.command;

import java.util.UUID;
import org.ticketing.reservation.domain.event.payload.CancelReason;

public record CancelReservationCommand(
        UUID reservationId,
        String canceledBy,
        CancelReason cancelReason
) {}

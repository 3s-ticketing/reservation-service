package org.ticketing.reservation.presentation.dto.internal;

import java.util.UUID;

public record InternalReservationStatusResponse(
        UUID reservationId,
        boolean isValid  // reservation PENDING && 모든 seat HOLD 여부
) {
    public static InternalReservationStatusResponse from(UUID reservationId, boolean isValid) {
        return new InternalReservationStatusResponse(reservationId, isValid);
    }
}
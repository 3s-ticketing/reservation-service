package org.ticketing.reservation.presentation.dto.internal;

import java.util.UUID;
import org.ticketing.reservation.application.dto.result.ReservationResult;

public record InternalReservationResponse(
        UUID reservationId,
        UUID userId,
        Long totalPrice
) {
    public static InternalReservationResponse from(ReservationResult result) {
        return new InternalReservationResponse(
                result.id(),
                result.userId(),
                result.totalPrice()
        );
    }
}
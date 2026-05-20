package org.ticketing.reservation.presentation.dto.internal;

import org.ticketing.reservation.application.dto.result.ReservationResult;

import java.util.UUID;

public record InternalReservationDetailResponse(
        UUID reservationId,
        UUID userId,
        Long totalPrice,
        boolean isValid
) {
    public static InternalReservationDetailResponse from(ReservationResult result, boolean isValid) {
        return new InternalReservationDetailResponse(
                result.id(),
                result.userId(),
                result.totalPrice(),
                isValid
        );
    }
}
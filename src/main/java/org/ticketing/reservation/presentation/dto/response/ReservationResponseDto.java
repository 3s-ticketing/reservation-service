package org.ticketing.reservation.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.domain.model.ReservationStatus;

public record ReservationResponseDto(
        UUID id,
        UUID userId,
        UUID matchId,
        ReservationStatus status,
        Long totalPrice,
        LocalDateTime createdAt,
        String createdBy
) {
    public static ReservationResponseDto from(ReservationResult result) {
        return new ReservationResponseDto(
                result.id(),
                result.userId(),
                result.matchId(),
                result.status(),
                result.totalPrice(),
                result.createdAt(),
                result.createdBy()
        );
    }
}

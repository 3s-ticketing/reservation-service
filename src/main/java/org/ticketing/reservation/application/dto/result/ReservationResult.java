package org.ticketing.reservation.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.model.ReservationStatus;

public record ReservationResult(
        UUID id,
        UUID userId,
        UUID matchId,
        ReservationStatus status,
        Long totalPrice,
        LocalDateTime createdAt,
        String createdBy
) {
    public static ReservationResult from(Reservation reservation) {
        return new ReservationResult(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getMatchId(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                reservation.getCreatedAt(),
                reservation.getCreatedBy()
        );
    }
}

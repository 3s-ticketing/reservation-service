package org.ticketing.reservation.application.dto.result;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.model.ReservationStatus;

/**
 * 예매 어그리게이트 조회 결과(루트 + 자식 좌석).
 */
public record ReservationResult(
        UUID id,
        UUID userId,
        UUID matchId,
        ReservationStatus status,
        Long totalPrice,
        List<ReservationSeatResult> seats,
        LocalDateTime createdAt,
        String createdBy
) {
    public static ReservationResult from(Reservation reservation) {
        List<ReservationSeatResult> seats = reservation.getSeats().stream()
                .map(ReservationSeatResult::from)
                .toList();
        return new ReservationResult(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getMatchId(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                seats,
                reservation.getCreatedAt(),
                reservation.getCreatedBy()
        );
    }
}

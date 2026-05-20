package org.ticketing.reservation.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.domain.model.ReservationStatus;

/**
 * 예매 어그리게이트(루트 + 자식 좌석) 응답.
 */
public record ReservationResponseDto(
        UUID id,
        UUID userId,
        UUID matchId,
        ReservationStatus status,
        Long totalPrice,
        List<ReservationSeatResponseDto> seats,
        LocalDateTime createdAt,
        String createdBy
) {
    public static ReservationResponseDto from(ReservationResult result) {
        List<ReservationSeatResponseDto> seats = result.seats().stream()
                .map(ReservationSeatResponseDto::from)
                .toList();
        return new ReservationResponseDto(
                result.id(),
                result.userId(),
                result.matchId(),
                result.status(),
                result.totalPrice(),
                seats,
                result.createdAt(),
                result.createdBy()
        );
    }
}

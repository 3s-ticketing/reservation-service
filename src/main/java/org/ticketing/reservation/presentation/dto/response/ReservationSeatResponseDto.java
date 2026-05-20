package org.ticketing.reservation.presentation.dto.response;

import java.util.UUID;
import org.ticketing.reservation.application.dto.result.ReservationSeatResult;
import org.ticketing.reservation.domain.model.ReservationSeatStatus;

/**
 * 자식 좌석의 응답 표현.
 */
public record ReservationSeatResponseDto(
        UUID id,
        UUID matchId,
        UUID stadiumId,
        UUID seatId,
        UUID seatGradeId,
        String seatNumber,
        ReservationSeatStatus seatStatus,
        Long price
) {
    public static ReservationSeatResponseDto from(ReservationSeatResult result) {
        return new ReservationSeatResponseDto(
                result.id(),
                result.matchId(),
                result.stadiumId(),
                result.seatId(),
                result.seatGradeId(),
                result.seatNumber(),
                result.seatStatus(),
                result.price()
        );
    }
}

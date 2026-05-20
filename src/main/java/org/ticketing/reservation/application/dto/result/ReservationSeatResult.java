package org.ticketing.reservation.application.dto.result;

import java.util.UUID;
import org.ticketing.reservation.domain.model.ReservationSeat;
import org.ticketing.reservation.domain.model.ReservationSeatStatus;

/**
 * 자식 좌석 엔티티의 애플리케이션 계층 표현.
 */
public record ReservationSeatResult(
        UUID id,
        UUID matchId,
        UUID stadiumId,
        UUID seatId,
        UUID seatGradeId,
        String seatNumber,
        ReservationSeatStatus seatStatus,
        Long price
) {
    public static ReservationSeatResult from(ReservationSeat seat) {
        return new ReservationSeatResult(
                seat.getId(),
                seat.getMatchId(),
                seat.getStadiumId(),
                seat.getSeatId(),
                seat.getSeatGradeId(),
                seat.getSeatNumber(),
                seat.getSeatStatus(),
                seat.getPrice()
        );
    }
}

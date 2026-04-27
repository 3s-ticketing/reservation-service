package org.ticketing.reservationseat.application.dto.result;

import java.util.UUID;
import org.ticketing.reservationseat.domain.model.entity.ReservationSeat;
import org.ticketing.reservationseat.domain.model.enums.ReservationSeatStatus;

public record ReservationSeatResult(
        UUID id,
        UUID reservationId,
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
                seat.getReservationId(),
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
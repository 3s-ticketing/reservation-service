package org.ticketing.reservation.application.dto;

import org.ticketing.reservationseat.domain.model.enums.ReservationSeatStatus;

import java.util.UUID;

public record ReservationSeatResponse(UUID id,
                                      UUID reservationId,
                                      UUID matchId,
                                      UUID stadiumId,
                                      UUID seatId,
                                      UUID seatGradeId,
                                      String seatNumber,
                                      ReservationSeatStatus seatStatus,
                                      Long price) {
}

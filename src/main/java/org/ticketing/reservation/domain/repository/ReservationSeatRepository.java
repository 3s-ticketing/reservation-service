package org.ticketing.reservation.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ticketing.reservation.domain.model.ReservationSeat;
import org.ticketing.reservation.domain.model.ReservationSeatStatus;

public interface ReservationSeatRepository {

    Optional<ReservationSeat> findActiveByMatchIdAndSeatId(UUID matchId, UUID seatId);

    List<ReservationSeat> findAllByMatchIdAndStatus(UUID matchId, ReservationSeatStatus status);

    List<ReservationSeat> findAllByReservationId(UUID reservationId);

    ReservationSeat save(ReservationSeat seat);

    ReservationSeat saveAndFlush(ReservationSeat seat);

    Optional<ReservationSeat> findById(UUID id);

    Optional<ReservationSeat> findActiveById(UUID id);
}
package org.ticketing.reservation.infrastructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.ticketing.reservation.domain.model.ReservationSeat;
import org.ticketing.reservation.domain.model.ReservationSeatStatus;
import org.ticketing.reservation.domain.repository.ReservationSeatRepository;

@Repository
@RequiredArgsConstructor
public class ReservationSeatRepositoryImpl implements ReservationSeatRepository {

    private final ReservationSeatJpaRepository jpaRepository;

    @Override
    public ReservationSeat save(ReservationSeat seat) {
        return jpaRepository.save(seat);
    }

    @Override
    public ReservationSeat saveAndFlush(ReservationSeat seat) {
        return jpaRepository.saveAndFlush(seat);
    }

    @Override
    public Optional<ReservationSeat> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<ReservationSeat> findActiveById(UUID id) {
        return jpaRepository.findActiveById(id);
    }

    @Override
    public Optional<ReservationSeat> findActiveByMatchIdAndSeatId(UUID matchId, UUID seatId) {
        return jpaRepository.findActiveByMatchIdAndSeatId(matchId, seatId);
    }

    @Override
    public List<ReservationSeat> findAllByReservationId(UUID reservationId) {
        return jpaRepository.findAllByReservationId(reservationId);
    }

    @Override
    public List<ReservationSeat> findAllByMatchIdAndStatus(UUID matchId, ReservationSeatStatus status) {
        return jpaRepository.findAllByMatchIdAndStatus(matchId, status);
    }
}
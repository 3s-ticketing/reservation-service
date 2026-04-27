package org.ticketing.reservationseat.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.ticketing.reservationseat.domain.model.entity.ReservationSeat;
import org.ticketing.reservationseat.domain.repository.ReservationSeatRepository;

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
    public List<ReservationSeat> findAllByReservationId(UUID reservationId) {
        return jpaRepository.findAllByReservationId(reservationId);
    }

    @Override
    public List<ReservationSeat> findHeldOlderThan(LocalDateTime threshold) {
        return jpaRepository.findHeldOlderThan(threshold);
    }

    // 서비스에서 직접 사용하는 추가 메서드 (도메인 인터페이스 외)
    public Optional<ReservationSeat> findHoldOrReservedSeat(UUID seatId, UUID matchId) {
        return jpaRepository.findHoldOrReservedSeat(seatId, matchId);
    }

    public int countActiveByReservationId(UUID reservationId) {
        return jpaRepository.countActiveByReservationId(reservationId);
    }
}
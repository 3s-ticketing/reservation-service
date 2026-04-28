package org.ticketing.reservation.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.model.ReservationStatus;
import org.ticketing.reservation.domain.repository.ReservationRepository;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private final JpaReservationRepository jpa;

    @Override
    public Reservation save(Reservation reservation) {
        return jpa.save(reservation);
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Reservation> findActiveById(UUID id) {
        return jpa.findActiveById(id);
    }

    @Override
    public List<Reservation> findAllByUserId(UUID userId) {
        return jpa.findAllByUserId(userId);
    }

    @Override
    public List<Reservation> findAllByMatchIdAndStatus(UUID matchId, ReservationStatus status) {
        return jpa.findAllByMatchIdAndStatus(matchId, status);
    }
}

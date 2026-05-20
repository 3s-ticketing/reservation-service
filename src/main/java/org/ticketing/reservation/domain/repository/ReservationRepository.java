package org.ticketing.reservation.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.model.ReservationStatus;

/**
 * Reservation 어그리게이트의 영속화 인터페이스.
 * 실제 구현은 infrastructure.persistence 에 위치한다.
 */
public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Reservation saveAndFlush(Reservation reservation); // ✅ 추가

    Optional<Reservation> findById(UUID id);

    Optional<Reservation> findActiveById(UUID id);

    List<Reservation> findAllByUserId(UUID userId);

    List<Reservation> findAllByMatchIdAndStatus(UUID matchId, ReservationStatus status);
}
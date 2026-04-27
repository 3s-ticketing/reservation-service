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

    Optional<Reservation> findById(UUID id);

    /** 소프트 삭제되지 않은 활성 예매만 조회. */
    Optional<Reservation> findActiveById(UUID id);

    List<Reservation> findAllByUserId(UUID userId);

    /** 특정 경기의 특정 상태 예매 일괄 조회 (경기 취소 등 후처리 용). */
    List<Reservation> findAllByMatchIdAndStatus(UUID matchId, ReservationStatus status);
}

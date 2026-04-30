package org.ticketing.reservation.domain.service;

import java.util.Optional;
import java.util.UUID;
import org.ticketing.reservation.domain.model.redis.SeatHold;

public interface SeatHoldRepository {

    // 좌석 선점 시도. NX EX 600 — 이미 선점 중이면 false
    boolean hold(UUID matchId, UUID seatId, SeatHold value);

    // 선점 정보 조회
    Optional<SeatHold> find(UUID matchId, UUID seatId);

    // 선점 해제
    void release(UUID matchId, UUID seatId);

    int countHoldsByReservationId(UUID reservationId);
}
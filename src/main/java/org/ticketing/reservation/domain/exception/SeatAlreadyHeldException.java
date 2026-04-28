package org.ticketing.reservation.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.ConflictException;

/**
 * 동일 경기·좌석에 대해 이미 활성(HOLD/RESERVED) 점유가 존재할 때 발생.
 * 도메인 가드 + DB 유니크 제약(uq_reservation_seat_active) 양쪽에서 사용된다.
 */
public class SeatAlreadyHeldException extends ConflictException {

    public SeatAlreadyHeldException(UUID matchId, UUID seatId) {
        super("이미 점유된 좌석입니다. matchId=" + matchId + ", seatId=" + seatId);
    }

    public SeatAlreadyHeldException() {
        super("이미 점유된 좌석입니다.");
    }
}

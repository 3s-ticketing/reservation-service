package org.ticketing.reservation.domain.model.redis;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Redis 좌석 락 페이로드.
 *
 * <p>키: {@code seat:{matchId}:{seatId}}
 *
 * <p>{@link SeatLockStatus} 별 TTL:
 * <ul>
 *   <li>{@link SeatLockStatus#HOLD} — 600초 (10분)</li>
 *   <li>{@link SeatLockStatus#RESERVED} — ticketOpenAt 기준 동적 계산
 *       ({@code SeatReservedTtlPolicy} 참고)</li>
 * </ul>
 */
public record SeatHold(
        SeatLockStatus state,
        UUID reservationId,
        UUID userId,
        UUID matchId,
        UUID seatId,
        OffsetDateTime expiresAt
) {

    /** HOLD 페이로드 생성. */
    public static SeatHold hold(UUID reservationId, UUID userId,
                                UUID matchId, UUID seatId,
                                OffsetDateTime expiresAt) {
        return new SeatHold(SeatLockStatus.HOLD, reservationId, userId, matchId, seatId, expiresAt);
    }

    /** RESERVED 페이로드 생성. */
    public static SeatHold reserved(UUID reservationId, UUID userId,
                                    UUID matchId, UUID seatId,
                                    OffsetDateTime expiresAt) {
        return new SeatHold(SeatLockStatus.RESERVED, reservationId, userId, matchId, seatId, expiresAt);
    }

    public boolean isOwnedBy(UUID userId, UUID reservationId) {
        return this.userId.equals(userId) && this.reservationId.equals(reservationId);
    }

    public boolean isHold() {
        return state == SeatLockStatus.HOLD;
    }

    public boolean isReserved() {
        return state == SeatLockStatus.RESERVED;
    }
}

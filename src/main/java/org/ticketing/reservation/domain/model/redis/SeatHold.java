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
 *   <li>{@link SeatLockStatus#HOLD} — 660초 (결제 10분 + 유예 1분)</li>
 *   <li>{@link SeatLockStatus#EXPIRE_PENDING} — HOLD TTL 잔여 시간 유지 (KEEPTTL),
 *       스케줄러가 결제 상태 확인 후 처리하기까지 최대 1분 유예</li>
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

    /** EXPIRE_PENDING 페이로드 생성. TTL은 기존 HOLD의 잔여 시간을 유지한다 (KEEPTTL). */
    public static SeatHold expirePending(UUID reservationId, UUID userId,
                                         UUID matchId, UUID seatId,
                                         OffsetDateTime expiresAt) {
        return new SeatHold(SeatLockStatus.EXPIRE_PENDING, reservationId, userId, matchId, seatId, expiresAt);
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

    public boolean isExpirePending() {
        return state == SeatLockStatus.EXPIRE_PENDING;
    }

    public boolean isReserved() {
        return state == SeatLockStatus.RESERVED;
    }
}

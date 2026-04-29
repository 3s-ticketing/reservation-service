package org.ticketing.reservation.domain.model.redis;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Redis 좌석 선점 정보.
 * Key: seat:{matchId}:{seatId}
 * TTL: 600초 (10분)
 */
public record SeatHold(
        UUID reservationId,
        UUID userId,
        UUID matchId,
        UUID seatId,
        OffsetDateTime expiresAt
) {
    public boolean isOwnedBy(UUID userId, UUID reservationId) {
        return this.userId.equals(userId) && this.reservationId.equals(reservationId);
    }
}
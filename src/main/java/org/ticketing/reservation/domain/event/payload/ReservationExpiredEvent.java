package org.ticketing.reservation.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * PENDING 상태가 TTL 을 초과하여 EXPIRED 로 전이될 때 발행되는 이벤트.
 * 점유 좌석을 RELEASE 하는 트리거가 된다.
 */
public record ReservationExpiredEvent(
        UUID reservationId,
        UUID userId,
        UUID matchId,
        OffsetDateTime expiredAt
) {
}

package org.ticketing.reservation.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reservation 어그리게이트 내 좌석이 HOLD 상태로 점유됐을 때 발행되는 이벤트.
 */
public record ReservationSeatHeldEvent(
        UUID reservationSeatId,
        UUID reservationId,
        UUID matchId,
        UUID seatId,
        Long price,
        OffsetDateTime heldAt
) {
}

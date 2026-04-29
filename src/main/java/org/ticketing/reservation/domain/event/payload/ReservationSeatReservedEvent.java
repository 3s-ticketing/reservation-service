package org.ticketing.reservation.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reservation 의 좌석이 결제 완료로 RESERVED 확정됐을 때 발행되는 이벤트.
 */
public record ReservationSeatReservedEvent(
        UUID reservationSeatId,
        UUID reservationId,
        UUID matchId,
        UUID seatId,
        OffsetDateTime reservedAt
) {
}

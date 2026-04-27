package org.ticketing.reservationseat.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.ticketing.reservationseat.domain.model.enums.ReservationSeatStatus;

/**
 * 좌석 점유가 해제(EXPIRED 또는 CANCELED)될 때 발행되는 이벤트.
 * 외부 좌석 인벤토리 시스템이 해당 좌석을 다시 가용 상태로 돌리는 트리거가 된다.
 */
public record ReservationSeatReleasedEvent(
        UUID reservationSeatId,
        UUID reservationId,
        UUID matchId,
        UUID seatId,
        ReservationSeatStatus reason,
        OffsetDateTime releasedAt
) {
}

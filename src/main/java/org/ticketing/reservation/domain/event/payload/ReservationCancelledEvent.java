package org.ticketing.reservation.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 예매 취소 이벤트.
 * 점유 좌석 해제, 티켓 무효화, 환불 등 후속 처리의 트리거로 사용된다.
 */
public record ReservationCancelledEvent(
        UUID reservationId,
        UUID userId,
        UUID matchId,
        OffsetDateTime canceledAt
) {
}

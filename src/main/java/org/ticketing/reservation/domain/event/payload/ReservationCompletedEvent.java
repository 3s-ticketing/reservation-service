package org.ticketing.reservation.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 결제 완료 후 예매가 확정되었을 때 발행되는 이벤트.
 * 티켓 발급 트리거로 ticket 어그리게이트가 소비한다.
 */
public record ReservationCompletedEvent(
        UUID reservationId,
        UUID userId,
        UUID matchId,
        Long totalPrice,
        OffsetDateTime completedAt
) {
}

package org.ticketing.reservation.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 예매가 PENDING 상태로 생성되었을 때 발행되는 도메인 이벤트.
 * 결제 서비스/좌석 점유 워크플로우의 시작 신호로 사용한다.
 */
public record ReservationCreatedEvent(
        UUID reservationId,
        UUID userId,
        UUID matchId,
        Long totalPrice,
        OffsetDateTime createdAt
) {
}

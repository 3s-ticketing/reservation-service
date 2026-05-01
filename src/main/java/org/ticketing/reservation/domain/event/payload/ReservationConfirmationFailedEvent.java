package org.ticketing.reservation.domain.event.payload;

import java.util.UUID;

/**
 * 예매 확정 실패 이벤트.
 *
 * <p>payment.completed 이벤트 수신 후 최대 재시도 횟수를 초과해도
 * 예매 확정에 실패한 경우 발행된다.
 * payment-service 가 이 이벤트를 소비하여 환불 처리를 수행한다.
 */
public record ReservationConfirmationFailedEvent(
        String paymentId,
        UUID reservationId,
        String reason
) {
}

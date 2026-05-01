package org.ticketing.reservation.infrastructure.messaging.consumer;

import java.util.UUID;

/**
 * payment-service 가 발행하는 payment.refunded 이벤트 페이로드.
 *
 * <p>{@code orderId} 는 reservation-service 의 reservationId 에 해당한다.
 */
public record PaymentRefundedEvent(
        UUID paymentId,
        UUID orderId   // = reservationId
) {
}

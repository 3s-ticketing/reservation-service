package org.ticketing.reservation.infrastructure.messaging.consumer;

/**
 * payment-service 가 발행하는 payment.completed 이벤트 페이로드.
 *
 * <p>{@code orderId} 는 reservation-service 의 reservationId 에 해당한다.
 */
public record PaymentCompletedEvent(
        String paymentId,
        String orderId   // = reservationId
) {
}

package org.ticketing.reservation.infrastructure.client.dto;

import org.ticketing.reservation.domain.service.PaymentStatusProvider.PaymentStatus;

public record PaymentStatusResponse(
        boolean success,
        String message,
        PaymentData data,
        String traceId
) {
    public record PaymentData(String status) {}
}
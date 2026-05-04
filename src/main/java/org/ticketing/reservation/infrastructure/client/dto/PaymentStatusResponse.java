package org.ticketing.reservation.infrastructure.client.dto;

import org.ticketing.reservation.domain.service.PaymentStatusProvider.PaymentStatus;

public record PaymentStatusResponse(
        PaymentStatus status
) {
}
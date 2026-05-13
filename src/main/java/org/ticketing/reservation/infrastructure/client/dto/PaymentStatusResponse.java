package org.ticketing.reservation.infrastructure.client.dto;

import java.util.UUID;

public record PaymentStatusResponse(
        UUID id,
        UUID reservationId,
        UUID userId,
        Long totalPrice,
        String status
) {}
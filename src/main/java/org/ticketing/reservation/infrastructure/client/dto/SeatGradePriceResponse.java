package org.ticketing.reservation.infrastructure.client.dto;

import java.util.UUID;

public record SeatGradePriceResponse(
        UUID seatGradeId,
        Long price
) {}
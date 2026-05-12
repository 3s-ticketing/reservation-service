package org.ticketing.reservation.infrastructure.client.dto;

import java.util.UUID;

public record SeatResponse(
        UUID seatId,
        UUID stadiumId,
        String column,
        Integer seatNumber,
        UUID seatGradeId,
        String gradeName
) {}
package org.ticketing.reservation.application.dto.query;

import java.util.UUID;

public record GetMyReservationsQuery(
        UUID userId
) {}

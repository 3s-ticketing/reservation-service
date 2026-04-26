package org.ticketing.reservation.application.dto;

import java.util.UUID;

public record ReservationRequest(UUID userId, UUID matchId, Long totalPrice) {
}

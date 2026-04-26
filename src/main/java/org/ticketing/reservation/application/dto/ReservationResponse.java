package org.ticketing.reservation.application.dto;

import org.ticketing.reservation.domain.model.enums.ReservationStatus;

import java.util.UUID;

public record ReservationResponse(UUID id, UUID userId, UUID matchId, ReservationStatus status, Long totalPrice) {
}

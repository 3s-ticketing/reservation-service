package org.ticketing.reservationseat.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationSeatReservedEvent(
        UUID reservationSeatId,
        UUID reservationId,
        UUID matchId,
        UUID seatId,
        OffsetDateTime reservedAt
) {
}

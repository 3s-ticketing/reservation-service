package org.ticketing.reservationseat.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationSeatHeldEvent(
        UUID reservationSeatId,
        UUID reservationId,
        UUID matchId,
        UUID seatId,
        Long price,
        OffsetDateTime heldAt
) {
}

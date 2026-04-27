package org.ticketing.ticket.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketUsedEvent(
        UUID ticketId,
        UUID reservationId,
        UUID userId,
        OffsetDateTime usedAt
) {
}

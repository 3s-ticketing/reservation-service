package org.ticketing.ticket.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketCanceledEvent(
        UUID ticketId,
        UUID reservationId,
        UUID userId,
        OffsetDateTime canceledAt
) {
}

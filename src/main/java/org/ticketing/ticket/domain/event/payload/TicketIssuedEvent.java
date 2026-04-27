package org.ticketing.ticket.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketIssuedEvent(
        UUID ticketId,
        UUID userId,
        UUID reservationId,
        String qr,
        OffsetDateTime issuedAt
) {
}

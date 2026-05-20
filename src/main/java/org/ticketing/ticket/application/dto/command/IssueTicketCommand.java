package org.ticketing.ticket.application.dto.command;

import java.util.UUID;

public record IssueTicketCommand(
        UUID userId,
        UUID reservationId
) {
}
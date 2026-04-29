package org.ticketing.ticket.application.dto.command;

import java.util.UUID;

public record CancelTicketCommand(
        UUID ticketId,
        UUID userId   // 요청자 검증용
) {
}
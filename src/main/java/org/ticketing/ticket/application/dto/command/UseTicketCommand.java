package org.ticketing.ticket.application.dto.command;

import java.util.UUID;

public record UseTicketCommand(
        UUID ticketId,
        UUID userId,
        boolean isAdmin  // ✅ 추가
) {}
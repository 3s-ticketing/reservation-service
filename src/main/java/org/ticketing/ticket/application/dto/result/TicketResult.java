package org.ticketing.ticket.application.dto.result;

import java.util.UUID;
import org.ticketing.ticket.domain.model.entity.Ticket;
import org.ticketing.ticket.domain.model.enums.TicketStatus;

public record TicketResult(
        UUID id,
        UUID userId,
        UUID reservationId,
        String qr,
        TicketStatus status
) {
    public static TicketResult from(Ticket ticket) {
        return new TicketResult(
                ticket.getId(),
                ticket.getUserId(),
                ticket.getReservationId(),
                ticket.getQr(),
                ticket.getStatus()
        );
    }
}
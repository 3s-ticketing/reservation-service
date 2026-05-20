package org.ticketing.ticket.infrastructure.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ticketing.reservation.domain.event.payload.ReservationCompletedEvent;
import org.ticketing.ticket.application.dto.command.IssueTicketCommand;
import org.ticketing.ticket.application.service.TicketService;

@Component
@RequiredArgsConstructor
public class TicketIssuanceListener {

    private final TicketService ticketService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,fallbackExecution = true)
    public void on(ReservationCompletedEvent event) {
        ticketService.issue(new IssueTicketCommand(
                event.userId(),
                event.reservationId()
        ));
    }
}
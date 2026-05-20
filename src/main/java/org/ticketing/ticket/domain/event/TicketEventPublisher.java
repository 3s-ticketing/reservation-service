package org.ticketing.ticket.domain.event;

import org.ticketing.ticket.domain.event.payload.TicketCanceledEvent;
import org.ticketing.ticket.domain.event.payload.TicketIssuedEvent;
import org.ticketing.ticket.domain.event.payload.TicketUsedEvent;

public interface TicketEventPublisher {

    void publishIssued(TicketIssuedEvent event);

    void publishUsed(TicketUsedEvent event);

    void publishCanceled(TicketCanceledEvent event);
}

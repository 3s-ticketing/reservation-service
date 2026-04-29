package org.ticketing.ticket.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing.ticket.domain.event.TicketEventPublisher;
import org.ticketing.ticket.domain.event.payload.TicketCanceledEvent;
import org.ticketing.ticket.domain.event.payload.TicketIssuedEvent;
import org.ticketing.ticket.domain.event.payload.TicketUsedEvent;

@Slf4j
@Component
public class TicketEventPublisherImpl implements TicketEventPublisher {

    @Override
    public void publishIssued(TicketIssuedEvent event) {
        log.info("[TicketEvent] ISSUED - ticketId={}, reservationId={}",
                event.ticketId(), event.reservationId());
    }

    @Override
    public void publishUsed(TicketUsedEvent event) {
        log.info("[TicketEvent] USED - ticketId={}, reservationId={}",
                event.ticketId(), event.reservationId());
    }

    @Override
    public void publishCanceled(TicketCanceledEvent event) {
        log.info("[TicketEvent] CANCELED - ticketId={}, reservationId={}",
                event.ticketId(), event.reservationId());
    }
}
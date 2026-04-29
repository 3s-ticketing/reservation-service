package org.ticketing.ticket.domain.exception;

import org.ticketing.common.exception.ConflictException;
import org.ticketing.ticket.domain.model.enums.TicketStatus;

public class InvalidTicketStateException extends ConflictException {

    public InvalidTicketStateException(TicketStatus current, TicketStatus target) {
        super("티켓 상태 전이가 허용되지 않습니다. " + current + " → " + target);
    }
}

package org.ticketing.ticket.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.NotFoundException;

public class TicketNotFoundException extends NotFoundException {

    public TicketNotFoundException(UUID id) {
        super("티켓을 찾을 수 없습니다. id=" + id);
    }

    public static TicketNotFoundException byReservation(UUID reservationId) {
        return new TicketNotFoundException(reservationId);
    }
}

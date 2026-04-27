package org.ticketing.reservationseat.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.NotFoundException;

public class ReservationSeatNotFoundException extends NotFoundException {

    public ReservationSeatNotFoundException(UUID id) {
        super("예약 좌석을 찾을 수 없습니다. id=" + id);
    }
}

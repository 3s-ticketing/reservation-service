package org.ticketing.reservationseat.domain.exception;

import org.ticketing.common.exception.ConflictException;
import org.ticketing.reservationseat.domain.model.ReservationSeatStatus;

public class InvalidReservationSeatStateException extends ConflictException {

    public InvalidReservationSeatStateException(ReservationSeatStatus current, ReservationSeatStatus target) {
        super("좌석 상태 전이가 허용되지 않습니다. " + current + " → " + target);
    }

    public InvalidReservationSeatStateException(String message) {
        super(message);
    }
}

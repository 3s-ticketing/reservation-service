package org.ticketing.reservation.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.NotFoundException;

public class ReservationNotFoundException extends NotFoundException {

    public ReservationNotFoundException(UUID reservationId) {
        super("예매를 찾을 수 없습니다. id=" + reservationId);
    }
}

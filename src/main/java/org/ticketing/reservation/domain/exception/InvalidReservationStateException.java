package org.ticketing.reservation.domain.exception;

import org.ticketing.common.exception.ConflictException;
import org.ticketing.reservation.domain.model.ReservationStatus;

/**
 * 예매 상태 전이가 허용되지 않을 때 발생.
 * 예) 이미 EXPIRED 된 예매를 COMPLETED 로 전이하려 시도하는 경우.
 */
public class InvalidReservationStateException extends ConflictException {

    public InvalidReservationStateException(ReservationStatus current, ReservationStatus target) {
        super("예매 상태 전이가 허용되지 않습니다. " + current + " → " + target);
    }

    public InvalidReservationStateException(String message) {
        super(message);
    }
}

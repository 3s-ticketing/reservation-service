package org.ticketing.reservation.domain.exception;

import org.springframework.http.HttpStatus;
import org.ticketing.common.exception.CustomException;

/**
 * 외부 서비스(seat-service, match-service 등) 호출 실패 시 발생하는 예외.
 *
 * <p>서킷 브레이커가 OPEN 상태이거나 타임아웃/오류로 폴백이 실행될 때 던진다.
 * HTTP 503 Service Unavailable 로 응답한다.
 */
public class ExternalServiceUnavailableException extends CustomException {
    public ExternalServiceUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}

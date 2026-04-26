package org.ticketing.reservation.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
    PENDING("예매 요청"),
    COMPLETED("예매 완료"),
    EXPIRED("예매 만료"),
    CANCELLED("예매 취소");

    private final String description;
}

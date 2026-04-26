package org.ticketing.reservation.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatStatus {
    AVAILABLE("예약 가능"),
    HOLD("예약중"),
    RESERVED("예약 완료"),
    EXPIRED("예약 만료"),
    CANCELED("예약 취소");

    private final String description;
}

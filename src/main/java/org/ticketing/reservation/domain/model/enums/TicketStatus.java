package org.ticketing.reservation.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketStatus {
    AVAILABLE("사용 가능"),
    USED("사용 완료"),
    CANCELED("사용불가(취소)");

    private final String description;
}

package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

/** 결제 완료 이벤트 수신 시 예매 확정 커맨드. */
public record ConfirmReservationCommand(
        UUID reservationId
) {}

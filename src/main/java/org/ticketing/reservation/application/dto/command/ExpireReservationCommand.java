package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

/** TTL 만료 이벤트 수신 시 예매 만료 커맨드. */
public record ExpireReservationCommand(
        UUID reservationId
) {}

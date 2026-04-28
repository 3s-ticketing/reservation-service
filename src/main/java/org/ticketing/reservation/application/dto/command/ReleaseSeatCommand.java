package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

/**
 * PENDING 예매에서 특정 좌석 한 건만 사용자 취소(CANCELED)하는 커맨드.
 */
public record ReleaseSeatCommand(
        UUID reservationId,
        UUID seatId
) {}

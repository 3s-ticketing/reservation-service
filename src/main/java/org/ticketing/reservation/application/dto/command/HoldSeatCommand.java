package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

/**
 * PENDING 예매에 좌석을 한 건 추가(HOLD)하는 커맨드.
 *
 * <p>좌석은 어그리게이트 루트({@code Reservation})를 통해서만 추가된다.
 */
public record HoldSeatCommand(
        UUID reservationId,
        UUID seatId
) {}

package org.ticketing.reservation.application.dto.command;

import java.util.UUID;

/**
 * 예매 생성 커맨드.
 *
 * <p>빈 PENDING 예매만 만든다. 좌석 추가는 별도로
 * {@code ReservationSeatService.holdSeat → confirmReservationSeat} 흐름으로 진행한다.
 */
public record CreateReservationCommand(
        UUID userId,
        UUID matchId
) {}

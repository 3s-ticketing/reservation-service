package org.ticketing.reservation.application.dto.command;

import java.util.List;
import java.util.UUID;

/**
 * 예매 생성 커맨드.
 *
 * <p>한 번의 트랜잭션 안에서 PENDING 예매와 HOLD 좌석들을 함께 생성한다.
 * {@code totalPrice} 는 외부 좌석 메타(가격) 합계로 도메인이 직접 계산하므로
 * 클라이언트가 별도 전달하지 않는다.
 */
public record CreateReservationCommand(
        UUID userId,
        UUID matchId,
        List<UUID> seatIds
) {}

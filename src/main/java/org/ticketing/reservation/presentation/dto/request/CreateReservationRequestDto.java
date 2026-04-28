package org.ticketing.reservation.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.ticketing.reservation.application.dto.command.CreateReservationCommand;

/**
 * 예매 생성 요청 DTO.
 *
 * <p>좌석 ID 목록을 함께 전달받아 한 트랜잭션에서 PENDING 예매와 HOLD 좌석을 생성한다.
 * {@code totalPrice} 는 외부 좌석 도메인에서 가져온 가격 합계로 서버가 계산한다.
 */
public record CreateReservationRequestDto(

        @NotNull(message = "경기 ID는 필수입니다.")
        UUID matchId,

        @NotEmpty(message = "좌석은 최소 한 좌석 이상 선택해야 합니다.")
        List<UUID> seatIds
) {
    public CreateReservationCommand toCommand(UUID userId) {
        return new CreateReservationCommand(userId, matchId, seatIds);
    }
}

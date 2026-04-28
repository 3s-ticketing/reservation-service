package org.ticketing.reservation.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.ticketing.reservation.application.dto.command.HoldSeatCommand;

/**
 * 기존 PENDING 예매에 좌석을 한 건 추가(HOLD)하는 요청 DTO.
 */
public record HoldSeatRequestDto(

        @NotNull(message = "좌석 ID는 필수입니다.")
        UUID seatId
) {
    public HoldSeatCommand toCommand(UUID reservationId) {
        return new HoldSeatCommand(reservationId, seatId);
    }
}

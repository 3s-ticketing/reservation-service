package org.ticketing.reservation.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.ticketing.reservation.application.dto.command.CreateReservationCommand;

/**
 * 예매 생성 요청 DTO.
 *
 * <p>빈 PENDING 예매만 만든다. 좌석 점유는 후속 호출
 * (POST /api/reservation-seats/hold) 로 진행.
 */
public record CreateReservationRequestDto(

        @NotNull(message = "경기 ID는 필수입니다.")
        UUID matchId
) {
    public CreateReservationCommand toCommand(UUID userId) {
        return new CreateReservationCommand(userId, matchId);
    }
}

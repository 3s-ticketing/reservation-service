package org.ticketing.reservation.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.ticketing.reservation.application.dto.command.CreateReservationCommand;

public record CreateReservationRequestDto(

        @NotNull(message = "경기 ID는 필수입니다.")
        UUID matchId,

        @NotNull(message = "총 금액은 필수입니다.")
        @Min(value = 0, message = "총 금액은 0 이상이어야 합니다.")
        Long totalPrice
) {
    public CreateReservationCommand toCommand(UUID userId) {
        return new CreateReservationCommand(userId, matchId, totalPrice);
    }
}

package org.ticketing.reservation.presentation.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.service.ReservationApplicationService;
import org.ticketing.reservation.presentation.dto.response.ReservationResponseDto;

/**
 * 내부 서비스 간 통신용 컨트롤러.
 *
 * <p>/internal/** 경로는 외부에 노출되지 않도록 게이트웨이에서 차단해야 한다.
 * <ul>
 *   <li>confirm — 결제 완료 이벤트 수신 후 예매 확정 (ReservationSeat·Ticket 연동은 별도 브랜치)</li>
 *   <li>expire  — TTL 만료 이벤트 수신 후 예매 만료</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/reservations")
@RequiredArgsConstructor
public class ReservationInternalController {

    private final ReservationApplicationService reservationApplicationService;

    /** POST /internal/reservations/{reservationId}/confirm — 예매 확정 */
    @PostMapping("/{reservationId}/confirm")
    public ReservationResponseDto confirm(@PathVariable UUID reservationId) {
        return ReservationResponseDto.from(
                reservationApplicationService.confirm(new ConfirmReservationCommand(reservationId))
        );
    }

    /** POST /internal/reservations/{reservationId}/expire — 예매 만료 */
    @PostMapping("/{reservationId}/expire")
    public ReservationResponseDto expire(@PathVariable UUID reservationId) {
        return ReservationResponseDto.from(
                reservationApplicationService.expire(new ExpireReservationCommand(reservationId))
        );
    }
}

package org.ticketing.reservation.presentation.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.reservation.application.dto.command.CancelReservationSeatCommand;
import org.ticketing.reservation.application.dto.command.ConfirmReservationSeatCommand;
import org.ticketing.reservation.application.dto.command.HoldReservationSeatCommand;
import org.ticketing.reservation.application.dto.request.ConfirmReservationSeatRequest;
import org.ticketing.reservation.application.dto.request.HoldReservationSeatRequest;
import org.ticketing.reservation.application.dto.result.ReservationSeatResult;
import org.ticketing.reservation.application.service.ReservationSeatService;

@RestController
@RequestMapping("/api/reservation-seats")
@RequiredArgsConstructor
public class ReservationSeatController {

    private final ReservationSeatService reservationSeatService;

    // 좌석 선점 (Redis HOLD)
    @PostMapping("/hold")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void holdSeat(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody HoldReservationSeatRequest request
    ) {
        reservationSeatService.holdSeat(
                new HoldReservationSeatCommand(userId, request.reservationId(), request.seatId())
        );
    }

    // 결제 확정 - DB INSERT (RESERVED)
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationSeatResult confirmReservationSeat(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody ConfirmReservationSeatRequest request
    ) {
        return reservationSeatService.confirmReservationSeat(
                new ConfirmReservationSeatCommand(userId, request.reservationId(), request.seatId())
        );
    }

    // 개별 좌석 취소 (RESERVED -> CANCELED)
    @DeleteMapping("/{reservationSeatId}")
    public ReservationSeatResult cancelReservationSeat(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID reservationSeatId
    ) {
        return reservationSeatService.cancelReservationSeat(
                new CancelReservationSeatCommand(userId, reservationSeatId)
        );
    }

    // 예약 좌석 상세 조회
    @GetMapping("/{reservationSeatId}")
    public ReservationSeatResult getReservationSeat(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID reservationSeatId
    ) {
        return reservationSeatService.getReservationSeat(reservationSeatId);
    }

    // 예매의 전체 좌석 조회
    @GetMapping
    public List<ReservationSeatResult> getReservationSeats(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam UUID reservationId
    ) {
        return reservationSeatService.getReservationSeats(reservationId);
    }
}
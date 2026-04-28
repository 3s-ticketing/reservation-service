package org.ticketing.reservationseat.presentation;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.reservationseat.application.dto.command.CreateReservationSeatCommand;
import org.ticketing.reservationseat.application.dto.request.CreateReservationSeatRequest;
import org.ticketing.reservationseat.application.dto.result.ReservationSeatResult;
import org.ticketing.reservationseat.application.service.ReservationSeatService;

@RestController
@RequestMapping("/api/v1/reservation-seats")
@RequiredArgsConstructor
public class ReservationSeatController {

    private final ReservationSeatService reservationSeatService;

    //예약 좌석 생성
     //일반 회원만 가능 - X-User-Id 헤더로 유저 식별 (Gateway JWT 인증/인가 처리)
    // TODO : 인증/인가 연결 후 수정
    @PostMapping
    public ReservationSeatResult createReservationSeat(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody CreateReservationSeatRequest request
    ) {
        return reservationSeatService.createReservationSeat(
                new CreateReservationSeatCommand(userId, request.reservationId(), request.seatId())
        );
    }

    // 전체 좌석 조회
    @GetMapping
    public List<ReservationSeatResult> getReservationSeats(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam UUID reservationId
    ) {
        return reservationSeatService.getReservationSeats(reservationId);
    }

    // 좌석 상세 조회
    @GetMapping("/{reservationSeatId}")
    public ReservationSeatResult getReservationSeat(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID reservationSeatId
    ) {
        return reservationSeatService.getReservationSeat(reservationSeatId);
    }
}
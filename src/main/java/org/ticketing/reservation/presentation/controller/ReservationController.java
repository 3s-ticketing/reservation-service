package org.ticketing.reservation.presentation.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.common.util.SecurityUtil;
import org.ticketing.reservation.application.dto.command.CancelReservationCommand;
import org.ticketing.reservation.application.dto.command.ReleaseSeatCommand;
import org.ticketing.reservation.application.dto.query.GetMyReservationsQuery;
import org.ticketing.reservation.application.dto.query.GetReservationQuery;
import org.ticketing.reservation.application.service.ReservationApplicationService;
import org.ticketing.reservation.infrastructure.security.SecurityContextProvider;
import org.ticketing.reservation.presentation.dto.request.CreateReservationRequestDto;
import org.ticketing.reservation.presentation.dto.request.HoldSeatRequestDto;
import org.ticketing.reservation.presentation.dto.response.ReservationResponseDto;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationApplicationService reservationApplicationService;
    private final SecurityContextProvider securityContextProvider;

    // ──────────────────────────────────────────
    // 예매 라이프사이클
    // ──────────────────────────────────────────

    /** POST /api/reservations — 예매 생성 (좌석 HOLD 동시 수행) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponseDto create(@RequestBody @Valid CreateReservationRequestDto request) {
        UUID userId = SecurityUtil.getCurrentUserIdOrThrow();
        return ReservationResponseDto.from(
                reservationApplicationService.create(request.toCommand(userId))
        );
    }

    /** GET /api/reservations — 내 예매 목록 조회 */
    @GetMapping
    public List<ReservationResponseDto> getMyReservations() {
        UUID userId = SecurityUtil.getCurrentUserIdOrThrow();
        return reservationApplicationService
                .findMyReservations(new GetMyReservationsQuery(userId))
                .stream()
                .map(ReservationResponseDto::from)
                .toList();
    }

    /** GET /api/reservations/{reservationId} — 예매 상세 조회 (좌석 포함) */
    @GetMapping("/{reservationId}")
    public ReservationResponseDto get(@PathVariable UUID reservationId) {
        return ReservationResponseDto.from(
                reservationApplicationService.findById(new GetReservationQuery(reservationId))
        );
    }

    /** DELETE /api/reservations/{reservationId} — 예매 전체 취소 */
    @DeleteMapping("/{reservationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID reservationId) {
        String canceledBy = securityContextProvider.getCurrentUsername();
        reservationApplicationService.cancel(new CancelReservationCommand(reservationId, canceledBy));
    }

    // ──────────────────────────────────────────
    // 좌석 단위 (루트 경유)
    // ──────────────────────────────────────────

    /** POST /api/reservations/{reservationId}/seats — 좌석 한 건 추가 (HOLD) */
    @PostMapping("/{reservationId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponseDto holdSeat(@PathVariable UUID reservationId,
                                           @RequestBody @Valid HoldSeatRequestDto request) {
        return ReservationResponseDto.from(
                reservationApplicationService.holdSeat(request.toCommand(reservationId))
        );
    }

    /** DELETE /api/reservations/{reservationId}/seats/{seatId} — 좌석 한 건만 해제 */
    @DeleteMapping("/{reservationId}/seats/{seatId}")
    public ReservationResponseDto releaseSeat(@PathVariable UUID reservationId,
                                              @PathVariable UUID seatId) {
        return ReservationResponseDto.from(
                reservationApplicationService.releaseSeat(new ReleaseSeatCommand(reservationId, seatId))
        );
    }
}

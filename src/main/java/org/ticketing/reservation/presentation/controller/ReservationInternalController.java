package org.ticketing.reservation.presentation.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.dto.query.GetReservationQuery;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.application.service.ReservationApplicationService;
import org.ticketing.reservation.domain.model.ReservationStatus;
import org.ticketing.reservation.domain.service.SeatHoldRepository;
import org.ticketing.reservation.presentation.dto.internal.InternalReservationDetailResponse;
import org.ticketing.reservation.presentation.dto.internal.InternalReservationResponse;
import org.ticketing.reservation.presentation.dto.internal.InternalReservationStatusResponse;
import org.ticketing.reservation.presentation.dto.response.ReservationResponseDto;

/**
 * 내부 서비스 간 통신용 컨트롤러.
 *
 * <p>/internal/** 경로는 외부에 노출되지 않도록 게이트웨이에서 차단해야 한다.
 * <ul>
 *   <li>confirm — 결제 완료 이벤트 수신 후 예매 확정. 루트가 COMPLETED 로 전이되며
 *       자식 좌석들도 RESERVED 로 함께 전이된다.</li>
 *   <li>expire  — TTL 만료 이벤트 수신 후 예매 만료. 루트가 EXPIRED 로 전이되며
 *       HOLD 좌석들도 EXPIRED 로 함께 전이된다.</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/reservations")
@RequiredArgsConstructor
public class ReservationInternalController {

    private final ReservationApplicationService reservationApplicationService;
    private final SeatHoldRepository seatHoldRepository;

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

    // 예매 정보 조회 (payment-service -> reservation-service)
    // reservationId에 해당하는 reservationId, userId, totalPrice 반환
    @GetMapping("/{reservationId}")
    public ResponseEntity<InternalReservationResponse> getReservation(
            @PathVariable UUID reservationId
    ) {
        ReservationResult result = reservationApplicationService.findById(
                new GetReservationQuery(reservationId)
        );
        return ResponseEntity.ok(InternalReservationResponse.from(result));
    }

    // 예매 상태 검증 (payment-service -> reservation-service)
    // reservation.status == PENDING && 모든 seat Redis HOLD 여부 반환
    @GetMapping("/{reservationId}/status")
    public ResponseEntity<InternalReservationStatusResponse> getReservationStatus(
            @PathVariable UUID reservationId
    ) {
        ReservationResult result = reservationApplicationService.findById(
                new GetReservationQuery(reservationId)
        );

        boolean isPending = result.status() == ReservationStatus.PENDING;

        boolean allSeatsHeld = result.seats().stream()
                .allMatch(seat -> seatHoldRepository
                        .find(result.matchId(), seat.seatId())
                        .isPresent());

        return ResponseEntity.ok(
                InternalReservationStatusResponse.from(reservationId, isPending && allSeatsHeld)
        );
    }

    @GetMapping("/{reservationId}/detail")
    public ResponseEntity<InternalReservationDetailResponse> getReservationDetail(
            @PathVariable UUID reservationId
    ) {
        ReservationResult result = reservationApplicationService.findById(
                new GetReservationQuery(reservationId)
        );

        boolean isPending = result.status() == ReservationStatus.PENDING;
        boolean allSeatsHeld = result.seats().stream()
                .allMatch(seat -> seatHoldRepository
                        .find(result.matchId(), seat.seatId())
                        .isPresent());

        return ResponseEntity.ok(
                InternalReservationDetailResponse.from(result, isPending && allSeatsHeld)
        );
    }
}

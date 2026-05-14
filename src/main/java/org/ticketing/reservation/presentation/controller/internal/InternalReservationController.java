package org.ticketing.reservation.presentation.controller.internal;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
 * 서비스 간 내부 통신 전용 컨트롤러.
 *
 * <p>{@code /internal/**} 경로는 API Gateway 에서 외부 노출이 차단된다.
 *
 * <h3>엔드포인트 목록</h3>
 * <ul>
 *   <li>POST /{reservationId}/confirm — 결제 완료 후 예매 확정 (payment-service 호출)</li>
 *   <li>POST /{reservationId}/expire  — TTL 만료 후 예매 만료 (스케줄러 호출)</li>
 *   <li>GET  /{reservationId}         — 예매 기본 정보 조회 (payment-service 호출)</li>
 *   <li>GET  /{reservationId}/status  — 결제 전 좌석 유효성 검증 (payment-service 호출)</li>
 *   <li>GET  /{reservationId}/detail  — 예매 상세 + 유효성 (payment-service 호출)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/reservations")
@RequiredArgsConstructor
public class InternalReservationController {

    private final ReservationApplicationService reservationApplicationService;
    private final SeatHoldRepository seatHoldRepository;

    // ──────────────────────────────────────────
    // 상태 전이
    // ──────────────────────────────────────────

    /** 예매 확정 — 결제 완료 이벤트 수신 후 payment-service 가 호출 */
    @PostMapping("/{reservationId}/confirm")
    public ReservationResponseDto confirm(@PathVariable UUID reservationId) {
        return ReservationResponseDto.from(
                reservationApplicationService.confirm(new ConfirmReservationCommand(reservationId))
        );
    }

    /** 예매 만료 — HOLD TTL 만료 시 스케줄러가 호출 */
    @PostMapping("/{reservationId}/expire")
    public ReservationResponseDto expire(@PathVariable UUID reservationId) {
        return ReservationResponseDto.from(
                reservationApplicationService.expire(new ExpireReservationCommand(reservationId))
        );
    }

    // ──────────────────────────────────────────
    // 조회
    // ──────────────────────────────────────────

    /** 예매 기본 정보 조회 — reservationId · userId · totalPrice 반환 */
    @GetMapping("/{reservationId}")
    public ResponseEntity<InternalReservationResponse> getReservation(
            @PathVariable UUID reservationId
    ) {
        ReservationResult result = reservationApplicationService.findById(
                new GetReservationQuery(reservationId)
        );
        return ResponseEntity.ok(InternalReservationResponse.from(result));
    }

    /**
     * 결제 전 유효성 검증 — {@code reservation.status == PENDING} 이고
     * 모든 좌석이 Redis 에 HOLD 상태인지 확인.
     *
     * <p>빈 좌석 목록은 유효하지 않은 것으로 판단한다 (vacuous truth 방지).
     */
    @GetMapping("/{reservationId}/status")
    public ResponseEntity<InternalReservationStatusResponse> getReservationStatus(
            @PathVariable UUID reservationId
    ) {
        ReservationResult result = reservationApplicationService.findById(
                new GetReservationQuery(reservationId)
        );

        boolean isPending = result.status() == ReservationStatus.PENDING;
        boolean hasSeats = !result.seats().isEmpty();
        boolean allSeatsHeld = hasSeats && result.seats().stream()
                .allMatch(seat -> seatHoldRepository
                        .find(result.matchId(), seat.seatId())
                        .isPresent());

        return ResponseEntity.ok(
                InternalReservationStatusResponse.from(reservationId, isPending && allSeatsHeld)
        );
    }

    /**
     * 예매 상세 정보 + 유효성 반환.
     *
     * <p>{@link #getReservationStatus} 와 동일한 유효성 로직을 포함하며,
     * totalPrice 등 상세 필드를 함께 반환한다.
     */
    @GetMapping("/{reservationId}/detail")
    public ResponseEntity<InternalReservationDetailResponse> getReservationDetail(
            @PathVariable UUID reservationId
    ) {
        ReservationResult result = reservationApplicationService.findById(
                new GetReservationQuery(reservationId)
        );

        boolean isPending = result.status() == ReservationStatus.PENDING;
        boolean hasSeats = !result.seats().isEmpty();
        boolean allSeatsHeld = hasSeats && result.seats().stream()
                .allMatch(seat -> seatHoldRepository
                        .find(result.matchId(), seat.seatId())
                        .isPresent());

        return ResponseEntity.ok(
                InternalReservationDetailResponse.from(result, isPending && allSeatsHeld)
        );
    }
}

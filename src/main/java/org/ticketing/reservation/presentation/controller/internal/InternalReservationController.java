package org.ticketing.reservation.presentation.controller.internal;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.dto.command.ConfirmReservationSeatCommand;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.dto.query.GetReservationQuery;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.application.service.ReservationApplicationService;
import org.ticketing.reservation.application.service.ReservationSeatService;
import org.ticketing.reservation.domain.model.ReservationStatus;
import org.ticketing.reservation.domain.model.redis.SeatHold;
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
 *   <li>GET  /{reservationId}/detail  — 예매 상세 + 유효성; HOLD 좌석을 멱등적으로 auto-confirm (payment-service 호출)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/internal/reservations")
@RequiredArgsConstructor
public class InternalReservationController {

    private final ReservationApplicationService reservationApplicationService;
    private final ReservationSeatService reservationSeatService;
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
     * 결제 전 유효성 검증.
     *
     * <p>아래 두 조건 중 하나라도 충족하면 {@code isValid = true}:
     * <ol>
     *   <li>DB 에 PENDING 상태의 예매가 있고, 모든 좌석이 Redis 에 HOLD/EXPIRE_PENDING 상태로 존재</li>
     *   <li>DB 에 PENDING 상태의 예매가 있고, DB 좌석이 이미 RESERVED 로 전이된 경우
     *       (auto-confirm 이 이미 수행된 경우)</li>
     * </ol>
     *
     * <p>Redis HOLD 좌석을 기준으로 하므로 {@code /detail} auto-confirm 이전에도 올바르게 동작한다.
     */
    @GetMapping("/{reservationId}/status")
    public ResponseEntity<InternalReservationStatusResponse> getReservationStatus(
            @PathVariable UUID reservationId
    ) {
        ReservationResult result = reservationApplicationService.findById(
                new GetReservationQuery(reservationId)
        );

        boolean isPending = result.status() == ReservationStatus.PENDING;

        // Redis HOLD 좌석 우선 확인 (auto-confirm 이전 단계 지원)
        List<SeatHold> heldSeats = seatHoldRepository.findAllHeldByReservationId(reservationId);
        boolean hasHeldSeats = !heldSeats.isEmpty();

        // DB 좌석이 이미 confirm 된 경우도 유효 (auto-confirm 이후 단계)
        boolean hasDbSeats = !result.seats().isEmpty();
        boolean allDbSeatsReservedInRedis = hasDbSeats && result.seats().stream()
                .allMatch(seat -> seatHoldRepository.find(result.matchId(), seat.seatId()).isPresent());

        boolean isValid = isPending && (hasHeldSeats || allDbSeatsReservedInRedis);

        return ResponseEntity.ok(
                InternalReservationStatusResponse.from(reservationId, isValid)
        );
    }

    /**
     * 예매 상세 정보 + 유효성 반환. HOLD 좌석을 멱등적으로 auto-confirm 한다.
     *
     * <h3>처리 흐름</h3>
     * <ol>
     *   <li>Redis {@code holds:{reservationId}} Set 에서 미확정(HOLD/EXPIRE_PENDING) 좌석 조회</li>
     *   <li>존재하는 경우 각 좌석에 대해 {@link ReservationSeatService#confirmReservationSeat} 호출
     *       — DB insert + seatProvider.fetchSnapshot() + totalPrice 재계산 + HOLD→RESERVED 전이</li>
     *   <li>confirm 이후 예매 정보를 재조회하여 최신 totalPrice 를 반환</li>
     * </ol>
     *
     * <h3>멱등성</h3>
     * <p>{@link SeatHoldRepository#confirm} Lua 스크립트가 HOLD/EXPIRE_PENDING 인 경우에만
     * RESERVED 로 전이하고 {@code holds:{reservationId}} Set 에서 제거한다.
     * 두 번째 호출 시 Set 이 비어 있으므로 confirmReservationSeat 를 건너뛴다.
     *
     * <h3>부분 실패 처리</h3>
     * <p>confirm 에 실패한 좌석이 하나라도 있으면 {@code isValid = false} 를 반환해 결제를 차단한다.
     * 이미 confirm 된 좌석은 DB/Redis 상태를 유지하고, 실패한 좌석은 TTL 자연 만료로 해제된다.
     * 각 confirm 은 독립 트랜잭션이므로 부분 커밋 롤백은 수행하지 않는다.
     */
    @GetMapping("/{reservationId}/detail")
    public ResponseEntity<InternalReservationDetailResponse> getReservationDetail(
            @PathVariable UUID reservationId
    ) {
        // 1. 미확정 HOLD 좌석 조회
        List<SeatHold> heldSeats = seatHoldRepository.findAllHeldByReservationId(reservationId);

        // 2. HOLD 좌석이 있으면 auto-confirm (멱등: 이미 RESERVED 전이된 좌석은 Set에서 제거되어 있음)
        boolean anyConfirmFailed = false;
        for (SeatHold hold : heldSeats) {
            try {
                reservationSeatService.confirmReservationSeat(
                        new ConfirmReservationSeatCommand(
                                hold.userId(),
                                hold.reservationId(),
                                hold.seatId()
                        )
                );
            } catch (Exception e) {
                anyConfirmFailed = true;
                log.warn("[InternalReservationController] auto-confirm 실패 — "
                        + "reservationId={}, seatId={}, reason={}",
                        reservationId, hold.seatId(), e.getMessage());
            }
        }

        // 3. 최신 예매 정보 재조회 (totalPrice 반영)
        ReservationResult result = reservationApplicationService.findById(
                new GetReservationQuery(reservationId)
        );

        // 4. 유효성 판단: confirm 실패가 하나라도 있으면 isValid = false
        boolean isPending = result.status() == ReservationStatus.PENDING;
        boolean hasSeats = !result.seats().isEmpty();
        boolean allSeatsReservedInRedis = hasSeats && result.seats().stream()
                .allMatch(seat -> seatHoldRepository.find(result.matchId(), seat.seatId()).isPresent());

        boolean isValid = isPending && allSeatsReservedInRedis && !anyConfirmFailed;

        return ResponseEntity.ok(
                InternalReservationDetailResponse.from(result, isValid)
        );
    }
}

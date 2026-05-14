package org.ticketing.reservation.presentation.controller.internal;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.reservation.application.dto.query.GetReservationQuery;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.application.service.ReservationApplicationService;
import org.ticketing.reservation.domain.model.ReservationStatus;
import org.ticketing.reservation.domain.service.SeatHoldRepository;
import org.ticketing.reservation.presentation.dto.internal.InternalReservationDetailResponse;
import org.ticketing.reservation.presentation.dto.internal.InternalReservationResponse;
import org.ticketing.reservation.presentation.dto.internal.InternalReservationStatusResponse;

@RestController
@RequestMapping("/internal/reservations")
@RequiredArgsConstructor
public class InternalReservationController {

    private final ReservationApplicationService reservationApplicationService;
    private final SeatHoldRepository seatHoldRepository;

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

        // allMatch()는 빈 스트림에서 true를 반환(vacuous truth)하므로 좌석 존재 여부를 먼저 검증한다.
        boolean hasSeats = !result.seats().isEmpty();
        boolean allSeatsHeld = hasSeats && result.seats().stream()
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
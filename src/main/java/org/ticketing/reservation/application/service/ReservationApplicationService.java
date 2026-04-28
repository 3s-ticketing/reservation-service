package org.ticketing.reservation.application.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.reservation.application.dto.command.CancelReservationCommand;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.dto.command.CreateReservationCommand;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.dto.query.GetMyReservationsQuery;
import org.ticketing.reservation.application.dto.query.GetReservationQuery;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.repository.ReservationRepository;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReservationApplicationService {

    private final ReservationRepository reservationRepository;

    // ──────────────────────────────────────────
    // 커맨드
    // ──────────────────────────────────────────

    @Transactional
    public ReservationResult create(CreateReservationCommand command) {
        Reservation reservation = Reservation.create(
                command.userId(),
                command.matchId(),
                command.totalPrice()
        );
        return ReservationResult.from(reservationRepository.save(reservation));
    }

    /**
     * 예매 취소 — 사용자 요청.
     * 소프트 딜리트 처리한다.
     */
    @Transactional
    public void cancel(CancelReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.cancel();
        reservation.delete(command.canceledBy());
    }

    /**
     * 예매 확정 — 결제 완료 이벤트 수신 시 내부 호출.
     * ReservationSeat·Ticket 연동은 별도 브랜치에서 구현한다.
     */
    @Transactional
    public ReservationResult confirm(ConfirmReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.complete();
        return ReservationResult.from(reservation);
    }

    /**
     * 예매 만료 — TTL 만료 이벤트 수신 시 내부 호출.
     */
    @Transactional
    public ReservationResult expire(ExpireReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.expire();
        return ReservationResult.from(reservation);
    }

    // ──────────────────────────────────────────
    // 쿼리
    // ──────────────────────────────────────────

    public ReservationResult findById(GetReservationQuery query) {
        return ReservationResult.from(getActive(query.reservationId()));
    }

    public List<ReservationResult> findMyReservations(GetMyReservationsQuery query) {
        return reservationRepository.findAllByUserId(query.userId())
                .stream()
                .map(ReservationResult::from)
                .toList();
    }

    // ──────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────

    private Reservation getActive(UUID reservationId) {
        return reservationRepository.findActiveById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}

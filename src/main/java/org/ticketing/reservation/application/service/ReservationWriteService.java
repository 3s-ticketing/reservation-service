package org.ticketing.reservation.application.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.reservation.application.dto.command.CancelReservationCommand;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.dto.command.CreateReservationCommand;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.repository.ReservationRepository;

/**
 * Reservation 어그리게이트 쓰기 전담 서비스.
 *
 * <p>모든 메서드는 {@code @Transactional} 아래에서 실행되며, DB 변경만 담당한다.
 * 외부 좌석 도메인(SeatProvider) 호출이나 Redis 정리는 호출자(orchestrator)가
 * 트랜잭션 바깥에서 처리한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationWriteService {

    private final ReservationRepository reservationRepository;

    // ──────────────────────────────────────────
    // 예매 생성 — 빈 PENDING 예매만 만든다 (좌석 없음).
    // ──────────────────────────────────────────

    public ReservationResult create(CreateReservationCommand command) {
        Reservation reservation = Reservation.create(command.userId(), command.matchId(), 0L);
        return ReservationResult.from(reservationRepository.save(reservation));
    }

    // ──────────────────────────────────────────
    // 예매 상태 전이
    // ──────────────────────────────────────────

    public void cancel(CancelReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.cancel();
        reservation.delete(command.canceledBy());
    }

    public ReservationResult confirm(ConfirmReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.complete();
        return ReservationResult.from(reservation);
    }

    public ReservationResult expire(ExpireReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.expire();
        return ReservationResult.from(reservation);
    }

    // ──────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────

    private Reservation getActive(UUID reservationId) {
        return reservationRepository.findActiveById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}

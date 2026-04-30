package org.ticketing.reservation.application.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.reservation.application.dto.command.CancelReservationCommand;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.dto.command.CreateReservationCommand;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.dto.command.HoldSeatCommand;
import org.ticketing.reservation.application.dto.command.ReleaseSeatCommand;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.domain.exception.SeatAlreadyHeldException;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.repository.ReservationRepository;
import org.ticketing.reservation.domain.service.SeatProvider.SeatSnapshot;

/**
 * Reservation 어그리게이트 쓰기 전담 서비스.
 *
 * <p>모든 메서드는 {@code @Transactional} 아래에서 실행된다.
 * 외부 좌석 도메인 I/O({@code SeatProvider})는 이 클래스에 없다.
 * 호출자({@link ReservationApplicationService})가 트랜잭션 바깥에서 검증·스냅샷 수집을
 * 완료한 뒤 이 서비스로 위임하면, DB 커넥션을 잡은 채 외부 HTTP 를 기다리는 위험이 없다.
 *
 * <h3>coderabbit 리뷰 대응</h3>
 * <p>기존 {@code ReservationApplicationService.create()} 는 {@code @Transactional} 안에서
 * {@code seatProvider.existsAndUsable} / {@code fetchSnapshot} 을 호출해 외부 I/O 동안
 * DB 커넥션이 점유됐다. 이 클래스로 분리함으로써 SeatProvider 호출은 트랜잭션 시작 전에,
 * DB 쓰기는 이 클래스의 단일 트랜잭션 안에서만 수행된다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationWriteService {

    private final ReservationRepository reservationRepository;

    // ──────────────────────────────────────────
    // 예매 생성
    // ──────────────────────────────────────────

    /**
     * 검증·수집된 스냅샷을 사용해 PENDING 예매와 HOLD 좌석을 한 트랜잭션에서 생성한다.
     *
     * <p>SeatProvider I/O 는 이미 호출자가 트랜잭션 바깥에서 완료했으므로
     * 이 메서드는 순수하게 도메인 객체 생성과 영속화만 담당한다.
     *
     * @param command   userId, matchId 포함 커맨드
     * @param snapshots 호출자가 미리 수집한 좌석 스냅샷 목록 (순서 보장 불필요)
     */
    public ReservationResult create(CreateReservationCommand command, List<SeatSnapshot> snapshots) {
        Reservation reservation = Reservation.create(command.userId(), command.matchId(), 0L);
        snapshots.forEach(reservation::addSeat);
        reservation.recalculateTotalPrice();

        try {
            return ReservationResult.from(reservationRepository.save(reservation));
        } catch (DataIntegrityViolationException e) {
            throw new SeatAlreadyHeldException();
        }
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
    // 좌석 단위 (루트 경유)
    // ──────────────────────────────────────────

    /**
     * 미리 수집된 스냅샷으로 PENDING 예매에 좌석 한 건을 추가한다.
     *
     * @param command  reservationId, seatId 포함 커맨드
     * @param snapshot 호출자가 미리 수집한 좌석 스냅샷
     */
    public ReservationResult holdSeat(HoldSeatCommand command, SeatSnapshot snapshot) {
        Reservation reservation = getActive(command.reservationId());
        reservation.addSeat(snapshot);
        reservation.recalculateTotalPrice();

        try {
            return ReservationResult.from(reservationRepository.save(reservation));
        } catch (DataIntegrityViolationException e) {
            throw new SeatAlreadyHeldException(reservation.getMatchId(), command.seatId());
        }
    }

    public ReservationResult releaseSeat(ReleaseSeatCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.releaseSeat(command.seatId());
        reservation.recalculateTotalPrice();
        return ReservationResult.from(reservationRepository.save(reservation));
    }

    // ──────────────────────────────────────────
    // 조회 (readOnly) — 오케스트레이터가 holdSeat 경로에서 matchId 확인용
    // ──────────────────────────────────────────

    /**
     * holdSeat 준비 단계에서 matchId 를 확인한다.
     *
     * <p>readOnly 트랜잭션으로 처리되어 이 메서드가 반환되면 DB 커넥션이 즉시 반환된다.
     * 이후 호출자가 SeatProvider(Feign)를 호출하는 동안 커넥션 풀을 점유하지 않는다.
     */
    @Transactional(readOnly = true)
    public UUID getMatchId(UUID reservationId) {
        return reservationRepository.findActiveById(reservationId)
                .map(Reservation::getMatchId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    // ──────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────

    private Reservation getActive(UUID reservationId) {
        return reservationRepository.findActiveById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}

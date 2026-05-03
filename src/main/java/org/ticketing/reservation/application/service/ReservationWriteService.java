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
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.domain.event.ReservationEventPublisher;
import org.ticketing.reservation.domain.event.payload.ReservationCancelledEvent;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.model.ReservationSeat;
import org.ticketing.reservation.domain.repository.ReservationRepository;

/**
 * Reservation 어그리게이트 쓰기 전담 서비스.
 *
 * <p>모든 메서드는 {@code @Transactional} 아래에서 실행되며, DB 변경만 담당한다.
 * 외부 좌석 도메인(SeatProvider) 호출이나 Redis 정리는 호출자(orchestrator)가
 * 트랜잭션 바깥에서 처리한다.
 *
 * <h3>SeatCleanupTarget 반환</h3>
 * <p>{@link #cancel}과 {@link #expire}는 {@link SeatCleanupTarget}을 반환한다.
 * 좌석 ID 캡처를 동일 트랜잭션 안에서 수행함으로써, 스냅샷과 DB 쓰기 사이에
 * 발생하는 동시 {@code confirmReservationSeat()} 호출로 인한 스냅샷 stale 문제를 방지한다.
 * 호출자는 트랜잭션 커밋 후 반환된 target 으로 Redis 락을 정리한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationWriteService {

    private final ReservationRepository reservationRepository;
    private final ReservationEventPublisher eventPublisher;

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

    /**
     * 예매를 CANCELLED 로 전이하고 Outbox 이벤트를 등록한다.
     *
     * <p>활성 좌석 ID 를 트랜잭션 안에서 캡처하여 {@link SeatCleanupTarget} 으로 반환한다.
     * 호출자는 커밋 후 이 target 으로 Redis 락을 해제해야 한다.
     */
    public SeatCleanupTarget cancel(CancelReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());

        // 트랜잭션 안에서 캡처 — DB 쓰기와 동일 스냅샷 보장
        List<UUID> activeSeatIds = reservation.getSeats().stream()
                .filter(s -> s.getSeatStatus().isActive())
                .map(ReservationSeat::getSeatId)
                .toList();
        UUID matchId = reservation.getMatchId();
        UUID reservationId = reservation.getId();
        UUID userId = reservation.getUserId();

        reservation.cancel();
        reservation.delete(command.canceledBy());

        // Outbox 트랜잭션 안에서 이벤트 등록 — payment-service 환불 트리거
        eventPublisher.publishCancelled(new ReservationCancelledEvent(
                reservation.getId(),
                reservation.getUserId(),
                command.cancelReason()
        ));

        return new SeatCleanupTarget(matchId, reservationId, userId, activeSeatIds);
    }

    public ReservationResult confirm(ConfirmReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.complete();
        return ReservationResult.from(reservation);
    }

    /**
     * 예매를 EXPIRED 로 전이한다.
     *
     * <p>활성 좌석 ID 를 트랜잭션 안에서 캡처하여 {@link SeatCleanupTarget} 으로 반환한다.
     * 호출자는 커밋 후 이 target 으로 Redis 락을 해제해야 한다.
     *
     * <p>만료 시점의 예매는 일반적으로 좌석이 없지만(HOLD 상태는 Redis 에만 존재),
     * {@code confirmReservationSeat()} 가 부분 실행된 엣지 케이스에서는
     * RESERVED 좌석이 DB 에 남아 있을 수 있다. 해당 좌석의 Redis 키도 함께 정리한다.
     */
    public SeatCleanupTarget expire(ExpireReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());

        // 트랜잭션 안에서 캡처 — DB 쓰기와 동일 스냅샷 보장
        List<UUID> activeSeatIds = reservation.getSeats().stream()
                .filter(s -> s.getSeatStatus().isActive())
                .map(ReservationSeat::getSeatId)
                .toList();
        UUID matchId = reservation.getMatchId();
        UUID reservationId = reservation.getId();
        UUID userId = reservation.getUserId();

        reservation.expire();

        return new SeatCleanupTarget(matchId, reservationId, userId, activeSeatIds);
    }

    // ──────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────

    private Reservation getActive(UUID reservationId) {
        return reservationRepository.findActiveById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}

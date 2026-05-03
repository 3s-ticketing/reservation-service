package org.ticketing.reservation.application.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.ticketing.reservation.domain.model.ReservationSeat;
import org.ticketing.reservation.domain.model.ReservationStatus;
import org.ticketing.reservation.domain.model.redis.SeatHold;
import org.ticketing.reservation.domain.repository.ReservationRepository;
import org.ticketing.reservation.domain.service.SeatHoldRepository;
import org.ticketing.reservation.infrastructure.redis.SeatReservedTtlPolicy;

/**
 * 예매 어그리게이트 오케스트레이션 서비스.
 *
 * <h3>역할 경계</h3>
 * <p>본 서비스는 예매(루트)의 라이프사이클(생성·확정·취소·만료)만 다룬다.
 * 좌석 점유는 Redis 가 단일 진실(single source of truth)이며,
 * 좌석 단위 hold/confirm/cancel 은 {@code ReservationSeatService} 가 담당한다.
 *
 * <h3>cancel/expire 시 Redis 정리</h3>
 * <p>{@code cancel} / {@code expire} 는 다음 순서로 동작한다:
 * <ol>
 *   <li>read tx 로 활성 좌석을 캡처</li>
 *   <li>{@link ReservationWriteService} 가 write tx 로 DB 변경 + 커밋</li>
 *   <li>커밋 후 Redis 락을 일괄 해제 — 다른 사용자가 좌석을 다시 점유 가능하도록</li>
 * </ol>
 * DB 가 롤백되어도 Redis 가 먼저 풀리는 일이 없도록 항상 DB 커밋 후에 Redis 를 정리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationApplicationService {

    private final ReservationRepository reservationRepository;
    private final ReservationWriteService reservationWriteService;
    private final SeatHoldRepository seatHoldRepository;
    private final SeatReservedTtlPolicy reservedTtlPolicy;

    // ──────────────────────────────────────────
    // 커맨드 — 예매 라이프사이클
    // ──────────────────────────────────────────

    /**
     * 예매 생성 — 빈 PENDING 예매만 만든다.
     *
     * <p>좌석 추가는 후속 호출 ({@code POST /api/reservation-seats/hold})로 진행된다.
     * 점유 여부는 Redis 가 단일 진실로 관리하므로 본 메서드는 외부 좌석 도메인을 호출하지 않는다.
     */
    public ReservationResult create(CreateReservationCommand command) {
        return reservationWriteService.create(command);
    }

    /**
     * 예매 취소 — 사용자 요청.
     *
     * <ol>
     *   <li>활성 좌석 ID 캡처 (read tx)</li>
     *   <li>DB 취소 (write tx 커밋)</li>
     *   <li>Redis 락 해제 (커밋 후) — 다른 사용자가 좌석을 다시 점유 가능하도록</li>
     * </ol>
     *
     * <p>DB 커밋 후 Redis 를 정리하므로 DB 가 롤백되어도 Redis 가 먼저 풀리는 일은 없다.
     * Redis 정리 자체가 실패하면 경고 로그만 남기고 진행 — TTL 자연 만료까지 락이 유지됨.
     * (보상 메커니즘은 추후 reconciliation 잡으로 도입 예정)
     */
    public void cancel(CancelReservationCommand command) {
        SeatCleanupTarget target = collectActiveSeats(command.reservationId());
        reservationWriteService.cancel(command);
        releaseSeatsAfterCommit(target);
    }

    /**
     * 예매 확정 — 결제 완료 이벤트 수신 시 내부 호출.
     *
     * <p>DB 상태를 COMPLETED 로 전이한 뒤, Redis 좌석이 아직 HOLD/EXPIRE_PENDING 상태인 경우
     * RESERVED 로 전이한다.
     *
     * <p>정상 흐름({@code confirmReservationSeat} 이미 호출됨)에서는 Redis 가 이미 RESERVED 이므로
     * skip 된다. 예외 케이스 — {@code confirmReservationSeat} 의 Redis confirm 이 실패한 뒤
     * HoldExpiryScheduler 가 EXPIRE_PENDING 으로 전이한 상황 — 에서도 결제 완료 시점에
     * RESERVED 전이를 보장한다.
     */
    public ReservationResult confirm(ConfirmReservationCommand command) {
        SeatCleanupTarget target = collectActiveSeats(command.reservationId());
        ReservationResult result = reservationWriteService.confirm(command);
        confirmSeatsAfterCommit(target);
        return result;
    }

    /**
     * 예매 만료 — TTL 만료 이벤트 수신 시 내부 호출.
     *
     * <p>cancel 과 동일하게 활성 좌석을 캡처한 뒤 DB 만료 → Redis 정리 순으로 진행.
     */
    public ReservationResult expire(ExpireReservationCommand command) {
        SeatCleanupTarget target = collectActiveSeats(command.reservationId());
        ReservationResult result = reservationWriteService.expire(command);
        releaseSeatsAfterCommit(target);
        return result;
    }

    // ──────────────────────────────────────────
    // 쿼리
    // ──────────────────────────────────────────

    /**
     * 특정 경기의 취소 가능한 예매 ID 목록 조회.
     *
     * <p>취소 가능 상태: {@code PENDING}(미결제), {@code COMPLETED}(결제 완료).
     * 경기 취소 이벤트 수신 시 일괄 처리 대상 목록을 조회하는 데 사용된다.
     *
     * @param matchId 경기 ID
     * @return 취소 가능한 예매 ID 목록 (없으면 빈 리스트)
     */
    @Transactional(readOnly = true)
    public List<UUID> findCancellableReservationIds(UUID matchId) {
        List<UUID> ids = new ArrayList<>();
        reservationRepository.findAllByMatchIdAndStatus(matchId, ReservationStatus.PENDING)
                .stream().map(Reservation::getId).forEach(ids::add);
        reservationRepository.findAllByMatchIdAndStatus(matchId, ReservationStatus.COMPLETED)
                .stream().map(Reservation::getId).forEach(ids::add);
        return ids;
    }

    @Transactional(readOnly = true)
    public ReservationResult findById(GetReservationQuery query) {
        Reservation reservation = reservationRepository.findActiveById(query.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(query.reservationId()));
        return ReservationResult.from(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResult> findMyReservations(GetMyReservationsQuery query) {
        return reservationRepository.findAllByUserId(query.userId())
                .stream()
                .map(ReservationResult::from)
                .toList();
    }

    // ──────────────────────────────────────────
    // Redis 정리 헬퍼
    // ──────────────────────────────────────────

    /**
     * cancel/expire 직전 활성 좌석을 캡처한다 (read tx 한 건).
     * 이 단계에서 잡힌 좌석들이 후속 Redis release 의 대상이 된다.
     */
    @Transactional(readOnly = true)
    protected SeatCleanupTarget collectActiveSeats(UUID reservationId) {
        Reservation reservation = reservationRepository.findActiveById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        List<UUID> seatIds = reservation.getSeats().stream()
                .filter(s -> s.getSeatStatus().isActive())
                .map(ReservationSeat::getSeatId)
                .toList();
        return new SeatCleanupTarget(reservation.getMatchId(), seatIds);
    }

    /**
     * 캡처해둔 좌석에 대해 Redis 락을 RESERVED 로 전이한다.
     *
     * <p>호출 시점은 DB write tx 가 이미 커밋된 직후라야 한다.
     * 이미 RESERVED 이거나 키가 없으면(자연 만료) skip.
     * 개별 실패는 로그만 남기고 다음 좌석으로 진행.
     */
    private void confirmSeatsAfterCommit(SeatCleanupTarget target) {
        Duration ttl = reservedTtlPolicy.ttlFor(target.matchId());
        target.seatIds().forEach(seatId -> {
            try {
                Optional<SeatHold> holdOpt = seatHoldRepository.find(target.matchId(), seatId);
                if (holdOpt.isEmpty() || holdOpt.get().isReserved()) {
                    return; // 키 없거나 이미 RESERVED — confirmReservationSeat 에서 처리됨
                }
                SeatHold hold = holdOpt.get();
                SeatHold reservedPayload = SeatHold.reserved(
                        hold.reservationId(), hold.userId(),
                        target.matchId(), seatId,
                        OffsetDateTime.now().plus(ttl)
                );
                boolean ok = seatHoldRepository.confirm(target.matchId(), seatId, reservedPayload, ttl);
                if (!ok) {
                    log.warn("[Redis] 예매 확정 HOLD→RESERVED 전이 실패 — "
                            + "matchId={}, seatId={}", target.matchId(), seatId);
                }
            } catch (Exception e) {
                log.warn("[Redis] 예매 확정 좌석 처리 실패 — 자연 만료 대기. matchId={}, seatId={}",
                        target.matchId(), seatId, e);
            }
        });
    }

    /**
     * 캡처해둔 좌석에 대해 Redis 락을 일괄 해제한다.
     * 호출 시점은 DB write tx 가 이미 커밋된 직후라야 한다.
     * 개별 release 실패는 로그만 남기고 다음 좌석으로 진행.
     */
    private void releaseSeatsAfterCommit(SeatCleanupTarget target) {
        target.seatIds().forEach(seatId -> {
            try {
                seatHoldRepository.release(target.matchId(), seatId);
            } catch (Exception e) {
                log.warn("[Redis] 좌석 락 해제 실패 — 자연 만료 대기. matchId={}, seatId={}",
                        target.matchId(), seatId, e);
            }
        });
    }

    /** cancel/expire 후 Redis 정리 대상. */
    private record SeatCleanupTarget(UUID matchId, List<UUID> seatIds) {}
}

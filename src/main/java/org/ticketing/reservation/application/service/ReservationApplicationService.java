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
 *   <li>{@link ReservationWriteService} 가 write tx 안에서 활성 좌석 ID 를 캡처 후 DB 변경 + 커밋</li>
 *   <li>커밋 후 반환된 {@link SeatCleanupTarget} 으로 Redis 락을 일괄 해제</li>
 * </ol>
 * 좌석 ID 캡처를 DB 쓰기와 동일 트랜잭션 안에서 수행함으로써,
 * 트랜잭션 경계 밖 pre-snapshot 을 사용할 때 발생하는 동시 {@code confirmReservationSeat()}
 * 호출로 인한 stale 스냅샷 문제를 방지한다.
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
     *   <li>DB 취소 + 활성 좌석 ID 캡처 (동일 write tx 안에서 수행 — stale 스냅샷 방지)</li>
     *   <li>Redis 락 해제 (커밋 후) — 다른 사용자가 좌석을 다시 점유 가능하도록</li>
     * </ol>
     *
     * <p>DB 커밋 후 Redis 를 정리하므로 DB 가 롤백되어도 Redis 가 먼저 풀리는 일은 없다.
     * Redis 정리 자체가 실패하면 경고 로그만 남기고 진행 — TTL 자연 만료까지 락이 유지됨.
     * (보상 메커니즘은 추후 reconciliation 잡으로 도입 예정)
     */
    public void cancel(CancelReservationCommand command) {
        SeatCleanupTarget target = reservationWriteService.cancel(command);
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
     * <p>cancel 과 동일하게, 활성 좌석 캡처를 DB 만료와 동일 트랜잭션 안에서 수행한 뒤
     * 커밋 후 Redis 정리를 진행한다.
     */
    public ReservationResult expire(ExpireReservationCommand command) {
        SeatCleanupTarget target = reservationWriteService.expire(command);
        releaseSeatsAfterCommit(target);
        return ReservationResult.from(
                reservationRepository.findActiveById(command.reservationId())
                        .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()))
        );
    }

    // ──────────────────────────────────────────
    // 소비자용 멱등 변형 — Kafka at-least-once 환경 전용
    // ──────────────────────────────────────────

    /**
     * 예매 취소 — Kafka 소비자 전용 멱등 변형.
     *
     * <p>예매가 없거나 이미 종착 상태(EXPIRED/CANCELLED)이면 조용히 반환한다.
     * 이를 통해 소비자가 도메인 예외를 직접 catch 할 필요 없이 at-least-once 재전송을
     * 안전하게 처리할 수 있다.
     *
     * <p>API/동기 경로에서는 {@link #cancel} 을 사용해 엄격한 예외를 유지한다.
     */
    public void cancelIfActive(CancelReservationCommand command) {
        Reservation reservation = reservationRepository.findActiveById(command.reservationId())
                .orElse(null);
        if (reservation == null) {
            log.info("[idempotent] 예매 없음 또는 이미 소프트 삭제됨 — skip. reservationId={}",
                    command.reservationId());
            return;
        }
        if (!reservation.getStatus().canTransitionTo(ReservationStatus.CANCELLED)) {
            log.info("[idempotent] 예매 취소 불가 상태 — skip. reservationId={}, status={}",
                    command.reservationId(), reservation.getStatus());
            return;
        }
        cancel(command);
    }

    /**
     * 예매 만료 — Kafka 소비자 전용 멱등 변형.
     *
     * <p>{@link #cancelIfActive} 와 동일한 설계 원칙을 따른다.
     * EXPIRED/CANCELLED 등 이미 종착 상태인 경우 조용히 반환한다.
     */
    public void expireIfActive(ExpireReservationCommand command) {
        Reservation reservation = reservationRepository.findActiveById(command.reservationId())
                .orElse(null);
        if (reservation == null) {
            log.info("[idempotent] 예매 없음 또는 이미 소프트 삭제됨 — skip. reservationId={}",
                    command.reservationId());
            return;
        }
        if (!reservation.getStatus().canTransitionTo(ReservationStatus.EXPIRED)) {
            log.info("[idempotent] 예매 만료 불가 상태 — skip. reservationId={}, status={}",
                    command.reservationId(), reservation.getStatus());
            return;
        }
        expire(command);
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
     * confirm 직전 활성 좌석을 캡처한다.
     *
     * <p>cancel / expire 는 {@link ReservationWriteService} 가 동일 write tx 안에서
     * 좌석 ID 를 캡처하므로 이 메서드를 사용하지 않는다.
     * confirm 에서만 pre-snapshot 이 사용된다 — confirm tx 와 사이에 좌석이 추가되더라도
     * {@code confirmReservationSeat()} 가 이미 해당 좌석의 Redis 를 RESERVED 로 전이한 뒤이므로
     * 누락이 무해하다.
     *
     * <p>{@code @Transactional} 을 선언하지 않는다. 같은 빈 내부에서 호출되면
     * Spring 프록시가 개입하지 않아 어노테이션이 무효화되기 때문이다.
     *
     * <p>트랜잭션 없이도 안전한 이유: {@code JpaReservationRepository.findActiveById} 는
     * {@code @EntityGraph(attributePaths = "seats")} 로 {@code seats} 를 쿼리 시점에 JOIN 하여
     * 즉시 로드한다. Spring Data JPA 가 해당 쿼리에 자체 read-only 트랜잭션을 부여하므로
     * {@code LazyInitializationException} 이 발생하지 않는다.
     *
     * <p>주의: {@code findActiveById} 구현이 변경되어 즉시 로딩 보장이 사라지면
     * 이 메서드도 함께 재검토해야 한다.
     */
    protected SeatCleanupTarget collectActiveSeats(UUID reservationId) {
        Reservation reservation = reservationRepository.findActiveById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        List<UUID> seatIds = reservation.getSeats().stream()
                .filter(s -> s.getSeatStatus().isActive())
                .map(ReservationSeat::getSeatId)
                .toList();
        return new SeatCleanupTarget(
                reservation.getMatchId(),
                reservation.getId(),
                reservation.getUserId(),
                seatIds);
    }

    /**
     * 캡처해둔 좌석에 대해 Redis 락을 RESERVED 로 전이/생성한다.
     *
     * <p>호출 시점은 DB write tx 가 이미 커밋된 직후라야 한다.
     *
     * <p>분기:
     * <ul>
     *   <li>이미 RESERVED → skip (정상 흐름에서 {@code confirmReservationSeat} 가 처리)</li>
     *   <li>HOLD / EXPIRE_PENDING 존재 → {@link SeatHoldRepository#confirm} 으로 atomic 전이</li>
     *   <li>키 없음 → {@link SeatHoldRepository#upsertReserved} 로 RESERVED 페이로드 새로 SET</li>
     * </ul>
     *
     * <p>키가 없는 케이스는 HOLD/EXPIRE_PENDING TTL 자연 만료 뒤 결제완료가 늦게 도착한 경우에
     * 발생한다. 이때 skip 하면 다른 사용자가 같은 좌석을 hold 할 수 있으므로 RESERVED 키를
     * 새로 생성해야 한다 — Redis 가 좌석 점유의 단일 진실이라는 계약을 유지하기 위함.
     *
     * <p>개별 실패는 로그만 남기고 다음 좌석으로 진행.
     */
    private void confirmSeatsAfterCommit(SeatCleanupTarget target) {
        Duration ttl = reservedTtlPolicy.ttlFor(target.matchId());
        target.seatIds().forEach(seatId -> {
            try {
                Optional<SeatHold> holdOpt = seatHoldRepository.find(target.matchId(), seatId);

                // 이미 RESERVED — confirmReservationSeat 에서 처리됨
                if (holdOpt.isPresent() && holdOpt.get().isReserved()) {
                    return;
                }

                SeatHold reservedPayload = SeatHold.reserved(
                        target.reservationId(),
                        target.userId(),
                        target.matchId(), seatId,
                        OffsetDateTime.now().plus(ttl)
                );

                if (holdOpt.isPresent()) {
                    // HOLD 또는 EXPIRE_PENDING → atomic 전이
                    boolean ok = seatHoldRepository.confirm(
                            target.matchId(), seatId, reservedPayload, ttl);
                    if (!ok) {
                        log.warn("[Redis] HOLD/EXPIRE_PENDING→RESERVED 전이 실패 — "
                                + "matchId={}, seatId={}", target.matchId(), seatId);
                    }
                } else {
                    // 키 없음 — TTL 자연 만료 후 결제완료가 늦게 도착한 보정 경로.
                    // RESERVED 를 새로 박아 다른 사용자의 점유를 차단한다.
                    seatHoldRepository.upsertReserved(
                            target.matchId(), seatId, reservedPayload, ttl);
                    log.info("[Redis] 만료된 키에 RESERVED 신규 SET — "
                            + "matchId={}, seatId={}, reservationId={}",
                            target.matchId(), seatId, target.reservationId());
                }
            } catch (Exception e) {
                log.warn("[Redis] 예매 확정 좌석 처리 실패 — matchId={}, seatId={}",
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

}

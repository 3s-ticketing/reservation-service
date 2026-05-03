package org.ticketing.reservation.infrastructure.scheduler;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.service.ReservationApplicationService;
import org.ticketing.reservation.domain.exception.InvalidReservationStateException;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.domain.model.redis.SeatHold;
import org.ticketing.reservation.domain.service.PaymentStatusProvider;
import org.ticketing.reservation.domain.service.PaymentStatusProvider.PaymentStatus;
import org.ticketing.reservation.domain.service.SeatHoldRepository;

/**
 * HOLD TTL 만료 감지 스케줄러.
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>{@code hold_expiry_index} Sorted Set 에서 결제 윈도우(10분)가 지난 HOLD 키 조회</li>
 *   <li>각 좌석에 대해 원자적으로 HOLD → EXPIRE_PENDING 전이 (Lua 스크립트)</li>
 *   <li>{@link PaymentStatusProvider} 로 결제 상태 조회 (현재는 Stub, 항상 UNKNOWN 반환)</li>
 *   <li>결제 상태에 따라:
 *     <ul>
 *       <li>{@link PaymentStatus#COMPLETED} → {@code confirm()} 호출</li>
 *       <li>{@link PaymentStatus#FAILED}    → {@code expire()} 호출 + Redis 락 즉시 해제</li>
 *       <li>{@link PaymentStatus#PENDING}   → 로그 기록, EXPIRE_PENDING 유지 (TTL 자연 만료)</li>
 *       <li>{@link PaymentStatus#UNKNOWN}   → 로그 기록, EXPIRE_PENDING 유지 (TTL 자연 만료)</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>멱등성</h3>
 * <p>EXPIRE_PENDING Lua 스크립트가 state == HOLD 인 경우에만 전이를 허용하므로
 * 다중 인스턴스 환경에서도 중복 처리가 발생하지 않는다.
 *
 * <h3>실패 안전성</h3>
 * <p>개별 좌석 처리 실패가 다른 좌석 처리에 영향을 미치지 않도록 try-catch 로 격리한다.
 * 처리되지 않은 좌석은 HOLD TTL 유예 시간(1분) 내에 자연 만료된다.
 *
 * <h3>Feign 미구현 주의</h3>
 * <p>{@link PaymentStatusProvider} Feign 구현이 없는 동안 모든 조회가 UNKNOWN 을 반환한다.
 * 이 경우 EXPIRE_PENDING 상태로 전이된 좌석은 TTL(1분 유예) 자연 만료로 해제된다.
 * Feign 구현 완료 후 COMPLETED/FAILED 분기가 실제로 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final SeatHoldRepository seatHoldRepository;
    private final ReservationApplicationService reservationApplicationService;
    private final PaymentStatusProvider paymentStatusProvider;

    /**
     * 30초마다 만료된 HOLD 키를 점검한다.
     *
     * <p>fixedDelay 를 사용해 이전 실행 완료 후 30초가 지난 시점에 재실행한다.
     * 처리량이 많으면 간격이 벌어질 수 있으나, 유예 시간(60초) 내에 처리되면 충분하다.
     */
    @Scheduled(fixedDelayString = "${scheduler.hold-expiry.delay-ms:30000}")
    public void checkExpiredHolds() {
        long nowEpoch = Instant.now().getEpochSecond();
        List<String> expiredKeys = seatHoldRepository.findExpiredHoldKeys(nowEpoch);

        if (expiredKeys.isEmpty()) {
            return;
        }

        log.info("[HoldExpiryScheduler] 만료 대상 HOLD {} 건 발견", expiredKeys.size());

        for (String member : expiredKeys) {
            try {
                processExpiredHold(member);
            } catch (Exception e) {
                log.error("[HoldExpiryScheduler] 처리 중 예외 발생 — member={}", member, e);
            }
        }
    }

    /**
     * 단일 HOLD 키에 대한 만료 처리.
     *
     * @param member hold_expiry_index 멤버 문자열 ("{matchId}:{seatId}")
     */
    private void processExpiredHold(String member) {
        String[] parts = member.split(":", 2);
        if (parts.length != 2) {
            log.warn("[HoldExpiryScheduler] 잘못된 멤버 형식 — member={}", member);
            return;
        }

        UUID matchId;
        UUID seatId;
        try {
            matchId = UUID.fromString(parts[0]);
            seatId  = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            log.warn("[HoldExpiryScheduler] UUID 파싱 실패 — member={}", member);
            return;
        }

        // 현재 HOLD 상태 조회 (reservationId 확보)
        SeatHold current = seatHoldRepository.find(matchId, seatId).orElse(null);
        if (current == null) {
            log.debug("[HoldExpiryScheduler] 키 없음 (이미 만료/해제) — matchId={}, seatId={}",
                    matchId, seatId);
            return;
        }

        if (!current.isHold()) {
            // 이미 EXPIRE_PENDING 이거나 RESERVED 상태: 중복 처리 방지
            log.debug("[HoldExpiryScheduler] HOLD 아닌 상태 (skip) — state={}, matchId={}, seatId={}",
                    current.state(), matchId, seatId);
            return;
        }

        UUID reservationId = current.reservationId();

        // 원자적 HOLD → EXPIRE_PENDING 전이
        SeatHold expirePendingPayload = SeatHold.expirePending(
                reservationId,
                current.userId(),
                matchId,
                seatId,
                current.expiresAt()   // expiresAt 은 HOLD 생성 시 설정된 원래 값 유지
        );
        boolean transitioned = seatHoldRepository.transitionToExpirePending(
                matchId, seatId, reservationId, expirePendingPayload);

        if (!transitioned) {
            // Lua 검증 실패: 다른 인스턴스가 이미 처리했거나 상태가 변경됨
            log.debug("[HoldExpiryScheduler] EXPIRE_PENDING 전이 실패 (다른 인스턴스가 처리했거나 상태 변경) "
                    + "— matchId={}, seatId={}", matchId, seatId);
            return;
        }

        log.info("[HoldExpiryScheduler] HOLD→EXPIRE_PENDING 전이 완료 — reservationId={}, "
                + "matchId={}, seatId={}", reservationId, matchId, seatId);

        // 결제 상태 조회
        PaymentStatus paymentStatus = paymentStatusProvider.getPaymentStatus(reservationId);
        log.info("[HoldExpiryScheduler] 결제 상태 조회 — reservationId={}, status={}",
                reservationId, paymentStatus);

        switch (paymentStatus) {
            case COMPLETED -> handlePaymentCompleted(reservationId, matchId, seatId);
            case FAILED    -> handlePaymentFailed(reservationId, matchId, seatId);
            case PENDING   -> log.info("[HoldExpiryScheduler] 결제 진행 중 — TTL 자연 만료 대기. "
                    + "reservationId={}", reservationId);
            case UNKNOWN   -> log.warn("[HoldExpiryScheduler] 결제 상태 조회 불가 (Feign 미구현 또는 오류) "
                    + "— TTL 자연 만료 대기. reservationId={}", reservationId);
        }
    }

    /**
     * 결제 완료 확인 → 예매 confirm 시도.
     *
     * <p>payment.completed 이벤트가 이미 수신되어 처리된 경우 {@link InvalidReservationStateException}
     * 로 idempotent 처리한다.
     */
    private void handlePaymentCompleted(UUID reservationId, UUID matchId, UUID seatId) {
        try {
            reservationApplicationService.confirm(new ConfirmReservationCommand(reservationId));
            log.info("[HoldExpiryScheduler] 예매 confirm 완료 — reservationId={}", reservationId);
        } catch (InvalidReservationStateException e) {
            log.info("[HoldExpiryScheduler] 예매 이미 확정 (idempotent skip) — reservationId={}",
                    reservationId);
        } catch (ReservationNotFoundException e) {
            log.warn("[HoldExpiryScheduler] 예매 없음 (skip) — reservationId={}", reservationId);
        }
    }

    /**
     * 결제 실패 확인 → 예매 expire 처리.
     *
     * <p>이미 종착 상태인 경우 idempotent 처리한다.
     * expire 는 내부적으로 DB 커밋 후 Redis 락을 해제하므로 별도 release 호출이 필요 없다.
     */
    private void handlePaymentFailed(UUID reservationId, UUID matchId, UUID seatId) {
        try {
            reservationApplicationService.expire(new ExpireReservationCommand(reservationId));
            log.info("[HoldExpiryScheduler] 예매 만료 처리 완료 — reservationId={}", reservationId);
        } catch (InvalidReservationStateException e) {
            log.info("[HoldExpiryScheduler] 예매 이미 만료/취소 (idempotent skip) — reservationId={}",
                    reservationId);
            // Redis 락은 TTL 자연 만료로 해제
        } catch (ReservationNotFoundException e) {
            log.warn("[HoldExpiryScheduler] 예매 없음 (skip) — reservationId={}", reservationId);
        }
    }
}

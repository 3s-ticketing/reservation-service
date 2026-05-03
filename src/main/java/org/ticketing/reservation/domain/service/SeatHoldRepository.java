package org.ticketing.reservation.domain.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ticketing.reservation.domain.model.redis.HoldResult;
import org.ticketing.reservation.domain.model.redis.SeatHold;

/**
 * 좌석 락(HOLD / RESERVED) Redis 진입점.
 *
 * <p>같은 키를 두 상태가 공유하므로 {@code hold()} 는 어떤 상태든 이미 점유 중이면 실패한다.
 * 이렇게 단일 SETNX 한 번으로 HOLD·RESERVED 양쪽 모두를 차단할 수 있다.
 */
public interface SeatHoldRepository {

    /**
     * 좌석 선점 시도 (원자적 cap 검사 포함).
     *
     * <p>단일 Lua 스크립트로 아래 동작을 원자적으로 수행한다:
     * <ol>
     *   <li>holds set 의 live 멤버를 확인해 현재 Redis HOLD 수를 계산하고,
     *       stale 멤버(이미 TTL 만료된 좌석)를 정리한다.</li>
     *   <li>{@code alreadyReserved + liveHoldCount >= maxCap} 이면 → {@link HoldResult#CAP_EXCEEDED}</li>
     *   <li>해당 좌석 키({@code seat:{matchId}:{seatId}})에 SETNX 시도.
     *       이미 점유 중이면 → {@link HoldResult#SEAT_TAKEN}</li>
     *   <li>성공 시 holds set 갱신, hold_expiry_index 등록 → {@link HoldResult#SUCCESS}</li>
     * </ol>
     *
     * @param alreadyReserved DB 에 이미 RESERVED 상태로 확정된 좌석 수 (호출자가 계산)
     * @param maxCap          예매당 허용 최대 좌석 수
     */
    HoldResult hold(UUID matchId, UUID seatId, SeatHold value, int alreadyReserved, int maxCap);

    /**
     * HOLD → RESERVED 전이. 결제 확정 시 호출.
     *
     * <p>원자성:
     * <ol>
     *   <li>현재 키가 존재하고</li>
     *   <li>state == HOLD 또는 EXPIRE_PENDING 이며</li>
     *   <li>reservationId 가 일치할 때만</li>
     *   <li>RESERVED 페이로드 + 새 TTL 로 덮어쓴다.</li>
     * </ol>
     * 위 4단계는 Lua 스크립트로 단일 원자 연산으로 처리된다.
     * EXPIRE_PENDING 상태에서 payment.completed 이벤트가 뒤늦게 도착하는 경우도 허용한다.
     *
     * @return 전이 성공 시 true. 키 만료/소실, owner 불일치, 이미 RESERVED 였음 등은 false.
     */
    boolean confirm(UUID matchId, UUID seatId, SeatHold reservedValue, Duration ttl);

    /** 현재 락 정보 조회 (HOLD 든 RESERVED 든). */
    Optional<SeatHold> find(UUID matchId, UUID seatId);

    /** 락 해제. cancel/expire 흐름에서 사용. */
    void release(UUID matchId, UUID seatId);

    /** 특정 reservation 의 활성 HOLD 개수 (예매당 최대 좌석 수 제한 등에 사용).
     *
     * <p>HOLD 와 EXPIRE_PENDING 상태 모두 카운트에 포함한다.
     * EXPIRE_PENDING 은 아직 confirm/release 되지 않은 좌석이므로 점유 중으로 간주한다.
     */
    int countHoldsByReservationId(UUID reservationId);

    /**
     * 결제 윈도우(10분)가 지난 HOLD 좌석 키 목록 조회.
     *
     * <p>{@code hold_expiry_index} Sorted Set 에서 score ≤ {@code maxEpochSeconds} 인
     * 멤버를 반환한다. 각 멤버 형식: {@code "{matchId}:{seatId}"}.
     *
     * @param maxEpochSeconds 현재 시각(epoch초) — 이 값 이하의 score 만 반환
     * @return 만료된 HOLD 키 목록 (비어 있을 수 있음)
     */
    List<String> findExpiredHoldKeys(long maxEpochSeconds);

    /**
     * HOLD → EXPIRE_PENDING 원자 전이.
     *
     * <p>Lua 스크립트로 GET → state/owner 검증 → SET(KEEPTTL) → ZREM 을 단일 원자 연산으로 수행.
     * 전이 성공 시 {@code hold_expiry_index} 에서 해당 항목을 제거해 중복 처리를 방지한다.
     *
     * @param matchId       경기 ID
     * @param seatId        좌석 ID
     * @param reservationId 소유자 검증용 예매 ID
     * @param expirePendingValue EXPIRE_PENDING 페이로드 (state = EXPIRE_PENDING)
     * @return 전이 성공 시 true. 키 없음 / state != HOLD / owner 불일치 시 false.
     */
    boolean transitionToExpirePending(UUID matchId, UUID seatId, UUID reservationId,
                                      SeatHold expirePendingValue);
}

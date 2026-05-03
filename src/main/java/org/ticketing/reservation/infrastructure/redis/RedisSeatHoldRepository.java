package org.ticketing.reservation.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.ticketing.common.exception.InternalServerException;
import org.ticketing.reservation.domain.model.redis.HoldResult;
import org.ticketing.reservation.domain.model.redis.SeatHold;
import org.ticketing.reservation.domain.service.SeatHoldRepository;

/**
 * 좌석 락 Redis 구현.
 *
 * <h3>키 구조</h3>
 * <pre>
 * seat:{matchId}:{seatId}     → JSON({state: HOLD|EXPIRE_PENDING|RESERVED, reservationId, ...})
 * holds:{reservationId}       → Set&lt;"{matchId}:{seatId}"&gt;  (예매당 활성 좌석 카운트용)
 * hold_expiry_index           → ZSet&lt;"{matchId}:{seatId}", score=expiry10minEpoch&gt;
 *                               (스케줄러가 HOLD→EXPIRE_PENDING 전이 대상 탐색에 사용)
 * </pre>
 *
 * <h3>holds set 멤버 형식</h3>
 * <p>멤버는 {@code "{matchId}:{seatId}"} 형식으로 저장된다.
 * 이 형식은 {@code hold_expiry_index} 의 멤버와 동일하며,
 * Lua 스크립트 안에서 {@code EXISTS seat:{member}} 로 live 여부를 확인할 수 있다.
 * TTL 자연 만료로 seat 키가 사라진 stale 멤버는 {@link #hold} / {@link #countHoldsByReservationId}
 * 호출 시 Lua 스크립트가 원자적으로 제거한다.
 *
 * <h3>HOLD → RESERVED 전이</h3>
 * <p>{@link #confirm} 은 Lua 스크립트로 GET → state/owner 검증 → SET 을 단일 원자
 * 연산으로 수행한다. HOLD 및 EXPIRE_PENDING 상태 모두 RESERVED 전이를 허용한다.
 *
 * <h3>HOLD → EXPIRE_PENDING 전이</h3>
 * <p>{@link #transitionToExpirePending} 은 별도 Lua 스크립트로 원자 전이 + 인덱스 제거를 수행한다.
 *
 * <h3>TTL</h3>
 * <ul>
 *   <li>HOLD: 660초 고정 (결제 10분 + 유예 1분)</li>
 *   <li>EXPIRE_PENDING: KEEPTTL (HOLD 잔여 시간 유지)</li>
 *   <li>RESERVED: {@code SeatReservedTtlPolicy} 가 ticketOpenAt 기반으로 계산해 전달</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatHoldRepository implements SeatHoldRepository {

    private static final String KEY_PREFIX = "seat:";
    private static final String HOLD_EXPIRY_INDEX_KEY = "hold_expiry_index";

    /** HOLD TTL: 결제 윈도우(600s) + 유예(60s) */
    static final Duration HOLD_TTL = Duration.ofSeconds(660);

    /** 결제 완료 기대 윈도우: 10분 */
    private static final long PAYMENT_WINDOW_SECONDS = 600L;

    /**
     * 원자적 좌석 선점 스크립트 (cap 검사 + stale 정리 포함).
     *
     * <pre>
     * KEYS[1] = seat:{matchId}:{seatId}      — 선점 키
     * KEYS[2] = holds:{reservationId}        — 예매당 hold set
     * KEYS[3] = hold_expiry_index            — 만료 스케줄 Sorted Set
     *
     * ARGV[1] = alreadyReserved              — 호출자가 계산한 DB RESERVED 수
     * ARGV[2] = maxCap                       — 허용 최대 좌석 수
     * ARGV[3] = seatHoldJson                 — HOLD 페이로드 JSON
     * ARGV[4] = holdTtlSeconds               — HOLD TTL (초)
     * ARGV[5] = holdSetMember                — holds set 에 추가할 멤버 ("{matchId}:{seatId}")
     * ARGV[6] = expiryScore                  — hold_expiry_index score (now + 600s epoch)
     * ARGV[7] = expiryMember                 — hold_expiry_index 멤버 ("{matchId}:{seatId}")
     * </pre>
     *
     * <p>반환:
     * <ul>
     *   <li>1  = 선점 성공</li>
     *   <li>0  = 해당 좌석 이미 점유 중 (SEAT_TAKEN)</li>
     *   <li>-1 = cap 초과 (CAP_EXCEEDED)</li>
     * </ul>
     *
     * <p>처리 순서:
     * <ol>
     *   <li>holds set 의 각 멤버에 대해 {@code EXISTS seat:{member}} 로 live 여부 확인.
     *       stale 멤버(키 없음)는 수집 후 일괄 SREM 으로 정리.</li>
     *   <li>{@code alreadyReserved + liveHoldCount >= maxCap} → return -1</li>
     *   <li>{@code SET KEYS[1] ARGV[3] NX EX ARGV[4]} — SETNX 실패 → return 0</li>
     *   <li>SADD, EXPIRE(holds set), ZADD(expiry index) → return 1</li>
     * </ol>
     */
    private static final RedisScript<Long> HOLD_SCRIPT = new DefaultRedisScript<>(
            "local members = redis.call('SMEMBERS', KEYS[2]) "
            + "local liveCount = 0 "
            + "local staleMembers = {} "
            + "for _, member in ipairs(members) do "
            + "  if redis.call('EXISTS', 'seat:' .. member) == 1 then "
            + "    liveCount = liveCount + 1 "
            + "  else "
            + "    table.insert(staleMembers, member) "
            + "  end "
            + "end "
            + "if tonumber(ARGV[1]) + liveCount >= tonumber(ARGV[2]) then "
            + "  return -1 "
            + "end "
            + "local ok = redis.call('SET', KEYS[1], ARGV[3], 'NX', 'EX', tonumber(ARGV[4])) "
            + "if not ok then "
            + "  return 0 "
            + "end "
            + "if #staleMembers > 0 then "
            + "  redis.call('SREM', KEYS[2], unpack(staleMembers)) "
            + "end "
            + "redis.call('SADD', KEYS[2], ARGV[5]) "
            + "redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4])) "
            + "redis.call('ZADD', KEYS[3], tonumber(ARGV[6]), ARGV[7]) "
            + "return 1",
            Long.class
    );

    /**
     * HOLD → RESERVED 전이 스크립트.
     *
     * <p>KEYS[1] = seat:{matchId}:{seatId}
     * <p>KEYS[2] = holds:{reservationId}
     * <p>KEYS[3] = hold_expiry_index (Sorted Set)
     * <p>ARGV[1] = 기대 reservationId (소유자 검증)
     * <p>ARGV[2] = 새 RESERVED 페이로드(JSON)
     * <p>ARGV[3] = 새 TTL(초)
     * <p>ARGV[4] = hold_expiry_index 에서 제거할 멤버 문자열 ("{matchId}:{seatId}")
     *
     * <p>HOLD 또는 EXPIRE_PENDING 상태에서 RESERVED 전이를 허용한다.
     * EXPIRE_PENDING 상태에서 결제 완료 이벤트가 뒤늦게 도착하는 경우를 처리한다.
     *
     * <p>SET + SREM(holds) + ZREM(hold_expiry_index) 를 단일 원자 연산으로 수행한다.
     * holds set 멤버는 "{matchId}:{seatId}" 형식이므로 JSON 에서 matchId + seatId 를 조합한다.
     *
     * <p>반환: 1 = 성공, 0 = 실패(키 없음 / state 불일치 / owner 불일치).
     */
    private static final RedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
            + "if not v then return 0 end "
            + "local obj = cjson.decode(v) "
            + "if (obj.state == 'HOLD' or obj.state == 'EXPIRE_PENDING') "
            + "    and obj.reservationId == ARGV[1] then "
            + "  redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[3])) "
            + "  redis.call('SREM', KEYS[2], obj.matchId .. ':' .. obj.seatId) "
            + "  redis.call('ZREM', KEYS[3], ARGV[4]) "
            + "  return 1 "
            + "else "
            + "  return 0 "
            + "end",
            Long.class
    );

    /**
     * HOLD → EXPIRE_PENDING 전이 스크립트.
     *
     * <p>KEYS[1] = seat:{matchId}:{seatId}
     * <p>KEYS[2] = hold_expiry_index (Sorted Set)
     * <p>ARGV[1] = 기대 reservationId (소유자 검증)
     * <p>ARGV[2] = 새 EXPIRE_PENDING 페이로드(JSON)
     * <p>ARGV[3] = hold_expiry_index 에서 제거할 멤버 문자열 ("{matchId}:{seatId}")
     *
     * <p>TTL 은 KEEPTTL 로 유지한다 (유예 시간 내 자연 만료 허용).
     * holds set 에는 변경 없음 — EXPIRE_PENDING 도 여전히 점유 중인 상태이므로.
     *
     * <p>반환: 1 = 성공, 0 = 실패(키 없음 / state != HOLD / owner 불일치).
     */
    private static final RedisScript<Long> EXPIRE_PENDING_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
            + "if not v then "
            + "  redis.call('ZREM', KEYS[2], ARGV[3]) "  // stale index entry 정리
            + "  return 0 "
            + "end "
            + "local obj = cjson.decode(v) "
            + "if obj.state == 'HOLD' and obj.reservationId == ARGV[1] then "
            + "  redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL') "
            + "  redis.call('ZREM', KEYS[2], ARGV[3]) "
            + "  return 1 "
            + "else "
            + "  return 0 "
            + "end",
            Long.class
    );

    /**
     * 예매당 live HOLD 수 조회 스크립트 (stale 멤버 정리 포함).
     *
     * <p>KEYS[1] = holds:{reservationId}
     *
     * <p>holds set 의 각 멤버({@code matchId:seatId})에 대해
     * {@code EXISTS seat:{member}} 로 live 여부를 확인한다.
     * stale 멤버는 일괄 SREM 후 live count 만 반환한다.
     */
    private static final RedisScript<Long> COUNT_LIVE_HOLDS_SCRIPT = new DefaultRedisScript<>(
            "local members = redis.call('SMEMBERS', KEYS[1]) "
            + "local liveCount = 0 "
            + "local staleMembers = {} "
            + "for _, member in ipairs(members) do "
            + "  if redis.call('EXISTS', 'seat:' .. member) == 1 then "
            + "    liveCount = liveCount + 1 "
            + "  else "
            + "    table.insert(staleMembers, member) "
            + "  end "
            + "end "
            + "if #staleMembers > 0 then "
            + "  redis.call('SREM', KEYS[1], unpack(staleMembers)) "
            + "end "
            + "return liveCount",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────
    // 키 헬퍼
    // ──────────────────────────────────────────

    private String seatKey(UUID matchId, UUID seatId) {
        return KEY_PREFIX + matchId + ":" + seatId;
    }

    private String holdSetKey(UUID reservationId) {
        return "holds:" + reservationId;
    }

    /**
     * holds set 및 hold_expiry_index 공용 멤버 문자열.
     * 형식: {@code "{matchId}:{seatId}"}
     */
    private String holdMember(UUID matchId, UUID seatId) {
        return matchId + ":" + seatId;
    }

    // ──────────────────────────────────────────
    // SeatHoldRepository 구현
    // ──────────────────────────────────────────

    /**
     * 좌석 선점 — cap 검사 + stale 정리 + SETNX + holds 갱신을 단일 Lua 트랜잭션으로 수행.
     *
     * <p>기존의 별도 카운트 조회 → 검사 → SETNX 패턴은 두 스레드가 동시에 카운트 검사를 통과해
     * cap 을 초과할 수 있는 TOCTOU 레이스가 있었다. HOLD_SCRIPT 는 이 전체 과정을
     * 원자적으로 처리한다.
     */
    @Override
    public HoldResult hold(UUID matchId, UUID seatId, SeatHold value, int alreadyReserved, int maxCap) {
        try {
            String json = objectMapper.writeValueAsString(value);
            String member = holdMember(matchId, seatId);
            double expiryScore = Instant.now().getEpochSecond() + PAYMENT_WINDOW_SECONDS;

            Long result = redisTemplate.execute(
                    HOLD_SCRIPT,
                    List.of(
                            seatKey(matchId, seatId),      // KEYS[1]
                            holdSetKey(value.reservationId()), // KEYS[2]
                            HOLD_EXPIRY_INDEX_KEY           // KEYS[3]
                    ),
                    String.valueOf(alreadyReserved),        // ARGV[1]
                    String.valueOf(maxCap),                 // ARGV[2]
                    json,                                   // ARGV[3]
                    String.valueOf(HOLD_TTL.getSeconds()),  // ARGV[4]
                    member,                                 // ARGV[5] — holds set 멤버
                    String.valueOf(expiryScore),            // ARGV[6] — expiry index score
                    member                                  // ARGV[7] — expiry index 멤버
            );

            if (result == null || result == 0L) return HoldResult.SEAT_TAKEN;
            if (result == -1L) return HoldResult.CAP_EXCEEDED;
            return HoldResult.SUCCESS;
        } catch (Exception e) {
            log.error("[Redis] 좌석 선점 실패 - matchId={}, seatId={}", matchId, seatId, e);
            throw new InternalServerException("Redis 좌석 선점 실패");
        }
    }

    @Override
    public boolean confirm(UUID matchId, UUID seatId, SeatHold reservedValue, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(reservedValue);
            Long result = redisTemplate.execute(
                    CONFIRM_SCRIPT,
                    List.of(
                            seatKey(matchId, seatId),
                            holdSetKey(reservedValue.reservationId()),
                            HOLD_EXPIRY_INDEX_KEY              // KEYS[3]
                    ),
                    reservedValue.reservationId().toString(),
                    json,
                    String.valueOf(ttl.getSeconds()),
                    holdMember(matchId, seatId)                // ARGV[4]
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[Redis] 좌석 confirm(HOLD|EXPIRE_PENDING→RESERVED) 실패 - matchId={}, seatId={}",
                    matchId, seatId, e);
            return false;
        }
    }

    @Override
    public Optional<SeatHold> find(UUID matchId, UUID seatId) {
        try {
            String json = redisTemplate.opsForValue().get(seatKey(matchId, seatId));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, SeatHold.class));
        } catch (Exception e) {
            log.error("[Redis] 좌석 선점 조회 실패 - matchId={}, seatId={}", matchId, seatId, e);
            return Optional.empty();
        }
    }

    /**
     * 좌석 락 해제.
     *
     * <p>holds set 에서 "{matchId}:{seatId}" 형식의 멤버를 제거한다.
     * hold_expiry_index 에서도 함께 제거한다 (HOLD 상태에서 직접 release 될 경우).
     */
    @Override
    public void release(UUID matchId, UUID seatId) {
        try {
            String json = redisTemplate.opsForValue().get(seatKey(matchId, seatId));
            if (json != null) {
                SeatHold hold = objectMapper.readValue(json, SeatHold.class);
                // 멤버 형식이 "{matchId}:{seatId}" 임에 주의
                redisTemplate.opsForSet().remove(
                        holdSetKey(hold.reservationId()), holdMember(matchId, seatId));
            }
            redisTemplate.delete(seatKey(matchId, seatId));
            redisTemplate.opsForZSet().remove(HOLD_EXPIRY_INDEX_KEY, holdMember(matchId, seatId));
        } catch (Exception e) {
            log.warn("[Redis] 좌석 선점 해제 실패 - TTL 자연 만료 대기. matchId={}, seatId={}",
                    matchId, seatId, e);
        }
    }

    /**
     * 예매당 활성 HOLD 수 조회.
     *
     * <p>holds set 의 멤버가 가리키는 seat 키 존재 여부를 확인해 live count 를 반환한다.
     * TTL 만료로 사라진 stale 멤버는 조회 시 원자적으로 정리된다.
     */
    @Override
    public int countHoldsByReservationId(UUID reservationId) {
        try {
            Long count = redisTemplate.execute(
                    COUNT_LIVE_HOLDS_SCRIPT,
                    List.of(holdSetKey(reservationId))
            );
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.warn("[Redis] HOLD 카운트 조회 실패 - 0 반환", e);
            return 0;
        }
    }

    @Override
    public List<String> findExpiredHoldKeys(long maxEpochSeconds) {
        try {
            Set<String> members = redisTemplate.opsForZSet().rangeByScore(
                    HOLD_EXPIRY_INDEX_KEY, 0, maxEpochSeconds);
            if (members == null || members.isEmpty()) {
                return Collections.emptyList();
            }
            return List.copyOf(members);
        } catch (Exception e) {
            log.warn("[Redis] hold_expiry_index 조회 실패", e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean transitionToExpirePending(UUID matchId, UUID seatId, UUID reservationId,
                                              SeatHold expirePendingValue) {
        try {
            String json = objectMapper.writeValueAsString(expirePendingValue);
            String member = holdMember(matchId, seatId);
            Long result = redisTemplate.execute(
                    EXPIRE_PENDING_SCRIPT,
                    List.of(seatKey(matchId, seatId), HOLD_EXPIRY_INDEX_KEY),
                    reservationId.toString(),
                    json,
                    member
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[Redis] HOLD→EXPIRE_PENDING 전이 실패 - matchId={}, seatId={}",
                    matchId, seatId, e);
            return false;
        }
    }
}

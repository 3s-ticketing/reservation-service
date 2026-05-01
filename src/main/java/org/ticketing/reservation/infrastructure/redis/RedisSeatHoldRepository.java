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
import org.ticketing.reservation.domain.model.redis.SeatHold;
import org.ticketing.reservation.domain.service.SeatHoldRepository;

/**
 * 좌석 락 Redis 구현.
 *
 * <h3>키 구조</h3>
 * <pre>
 * seat:{matchId}:{seatId}     → JSON({state: HOLD|EXPIRE_PENDING|RESERVED, reservationId, ...})
 * holds:{reservationId}       → Set&lt;seatId&gt;  (예매당 활성 좌석 카운트용)
 * hold_expiry_index           → ZSet&lt;"{matchId}:{seatId}", score=expiry10minEpoch&gt;
 *                               (스케줄러가 HOLD→EXPIRE_PENDING 전이 대상 탐색에 사용)
 * </pre>
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
     * HOLD → RESERVED 전이 스크립트.
     *
     * <p>KEYS[1] = seat:{matchId}:{seatId}
     * <p>KEYS[2] = holds:{reservationId}
     * <p>ARGV[1] = 기대 reservationId (소유자 검증)
     * <p>ARGV[2] = 새 RESERVED 페이로드(JSON)
     * <p>ARGV[3] = 새 TTL(초)
     *
     * <p>HOLD 또는 EXPIRE_PENDING 상태에서 RESERVED 전이를 허용한다.
     * EXPIRE_PENDING 상태에서 결제 완료 이벤트가 뒤늦게 도착하는 경우를 처리한다.
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
            + "  redis.call('SREM', KEYS[2], obj.seatId) "
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

    /** hold_expiry_index Sorted Set 의 멤버 문자열. */
    private String expiryMember(UUID matchId, UUID seatId) {
        return matchId + ":" + seatId;
    }

    // ──────────────────────────────────────────
    // SeatHoldRepository 구현
    // ──────────────────────────────────────────

    @Override
    public boolean hold(UUID matchId, UUID seatId, SeatHold value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(seatKey(matchId, seatId), json, HOLD_TTL);

            if (Boolean.TRUE.equals(success)) {
                // holds Set: 예매당 활성 좌석 카운트
                redisTemplate.opsForSet().add(holdSetKey(value.reservationId()), seatId.toString());
                redisTemplate.expire(holdSetKey(value.reservationId()), HOLD_TTL);

                // hold_expiry_index: score = now + 결제 윈도우(600s)
                double expiryScore = Instant.now().getEpochSecond() + PAYMENT_WINDOW_SECONDS;
                redisTemplate.opsForZSet().add(
                        HOLD_EXPIRY_INDEX_KEY,
                        expiryMember(matchId, seatId),
                        expiryScore
                );
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("[Redis] 좌석 선점 실패", e);
            throw new InternalServerException("Redis 좌석 선점 실패");
        }
    }

    @Override
    public boolean confirm(UUID matchId, UUID seatId, SeatHold reservedValue, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(reservedValue);
            Long result = redisTemplate.execute(
                    CONFIRM_SCRIPT,
                    List.of(seatKey(matchId, seatId), holdSetKey(reservedValue.reservationId())),
                    reservedValue.reservationId().toString(),
                    json,
                    String.valueOf(ttl.getSeconds())
            );
            if (result != null && result == 1L) {
                // hold_expiry_index 에서도 제거 (EXPIRE_PENDING 전이 전에 결제 완료된 경우)
                redisTemplate.opsForZSet().remove(
                        HOLD_EXPIRY_INDEX_KEY, expiryMember(matchId, seatId));
                return true;
            }
            return false;
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

    @Override
    public void release(UUID matchId, UUID seatId) {
        try {
            String json = redisTemplate.opsForValue().get(seatKey(matchId, seatId));
            if (json != null) {
                SeatHold hold = objectMapper.readValue(json, SeatHold.class);
                redisTemplate.opsForSet().remove(holdSetKey(hold.reservationId()), seatId.toString());
            }
            redisTemplate.delete(seatKey(matchId, seatId));
            // hold_expiry_index 에서도 정리 (HOLD 상태에서 직접 release 될 경우)
            redisTemplate.opsForZSet().remove(HOLD_EXPIRY_INDEX_KEY, expiryMember(matchId, seatId));
        } catch (Exception e) {
            log.warn("[Redis] 좌석 선점 해제 실패 - TTL 자연 만료 대기", e);
        }
    }

    @Override
    public int countHoldsByReservationId(UUID reservationId) {
        try {
            Long count = redisTemplate.opsForSet().size(holdSetKey(reservationId));
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
            String member = expiryMember(matchId, seatId);
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

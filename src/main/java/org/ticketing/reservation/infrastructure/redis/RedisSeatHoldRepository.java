package org.ticketing.reservation.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
 * <h3>키 구조 (옵션 A — 단일 키 + state in payload)</h3>
 * <pre>
 * seat:{matchId}:{seatId}     → JSON({state: HOLD|RESERVED, reservationId, userId, ...})
 * holds:{reservationId}       → Set&lt;seatId&gt; (예매당 HOLD 좌석 카운트용)
 * </pre>
 *
 * <h3>HOLD → RESERVED 전이</h3>
 * <p>{@link #confirm} 은 Lua 스크립트로 GET → state/owner 검증 → SET 을 단일 원자
 * 연산으로 수행한다. 검증 실패 시 0 을 반환한다.
 *
 * <h3>TTL</h3>
 * <ul>
 *   <li>HOLD: 600초 고정</li>
 *   <li>RESERVED: {@code SeatReservedTtlPolicy} 가 ticketOpenAt 기반으로 계산해 전달</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatHoldRepository implements SeatHoldRepository {

    private static final String KEY_PREFIX = "seat:";
    private static final Duration HOLD_TTL = Duration.ofSeconds(600);

    /**
     * HOLD → RESERVED 전이 스크립트.
     *
     * <p>KEYS[1] = seat:{matchId}:{seatId}
     * <p>KEYS[2] = holds:{reservationId} (HOLD set, 카운트용)
     * <p>ARGV[1] = 기대 reservationId (소유자 검증)
     * <p>ARGV[2] = 새 RESERVED 페이로드(JSON)
     * <p>ARGV[3] = 새 TTL(초)
     *
     * <p>반환: 1 = 성공, 0 = 실패(키 없음 / state != HOLD / owner 불일치).
     */
    private static final RedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
            + "if not v then return 0 end "
            + "local obj = cjson.decode(v) "
            + "if obj.state == 'HOLD' and obj.reservationId == ARGV[1] then "
            + "  redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[3])) "
            + "  redis.call('SREM', KEYS[2], obj.seatId) "
            + "  return 1 "
            + "else "
            + "  return 0 "
            + "end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private String key(UUID matchId, UUID seatId) {
        return KEY_PREFIX + matchId + ":" + seatId;
    }

    private String holdSetKey(UUID reservationId) {
        return "holds:" + reservationId;
    }

    @Override
    public boolean hold(UUID matchId, UUID seatId, SeatHold value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key(matchId, seatId), json, HOLD_TTL);

            if (Boolean.TRUE.equals(success)) {
                redisTemplate.opsForSet().add(holdSetKey(value.reservationId()), seatId.toString());
                redisTemplate.expire(holdSetKey(value.reservationId()), HOLD_TTL);
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
                    List.of(key(matchId, seatId), holdSetKey(reservedValue.reservationId())),
                    reservedValue.reservationId().toString(),
                    json,
                    String.valueOf(ttl.getSeconds())
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[Redis] 좌석 confirm(HOLD→RESERVED) 실패 - matchId={}, seatId={}",
                    matchId, seatId, e);
            return false;
        }
    }

    @Override
    public Optional<SeatHold> find(UUID matchId, UUID seatId) {
        try {
            String json = redisTemplate.opsForValue().get(key(matchId, seatId));
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
            String json = redisTemplate.opsForValue().get(key(matchId, seatId));
            if (json != null) {
                SeatHold hold = objectMapper.readValue(json, SeatHold.class);
                redisTemplate.opsForSet().remove(holdSetKey(hold.reservationId()), seatId.toString());
            }
            redisTemplate.delete(key(matchId, seatId));
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
}

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

    private static final RedisScript<Long> EXPIRE_PENDING_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if not v then "
                    + "  redis.call('ZREM', KEYS[2], ARGV[3]) "
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

    /**
     * 소유권 검증 후 조건부 삭제 스크립트.
     *
     * KEYS[1] = seat:{matchId}:{seatId}
     * KEYS[2] = holds:{reservationId}
     * ARGV[1] = reservationId (소유자 검증)
     * ARGV[2] = userId        (소유자 검증)
     * ARGV[3] = holdMember    ("{matchId}:{seatId}")
     *
     * 반환: 1 = 삭제 성공, 0 = 키 없음 또는 소유자 불일치
     */
    private static final RedisScript<Long> RELEASE_IF_OWNED_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if not v then return 0 end "
                    + "local obj = cjson.decode(v) "
                    + "if obj.reservationId == ARGV[1] and obj.userId == ARGV[2] then "
                    + "  redis.call('DEL', KEYS[1]) "
                    + "  redis.call('SREM', KEYS[2], ARGV[3]) "
                    + "  redis.call('ZREM', '" + HOLD_EXPIRY_INDEX_KEY + "', ARGV[3]) "
                    + "  return 1 "
                    + "else "
                    + "  return 0 "
                    + "end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private String seatKey(UUID matchId, UUID seatId) {
        return KEY_PREFIX + matchId + ":" + seatId;
    }

    private String holdSetKey(UUID reservationId) {
        return "holds:" + reservationId;
    }

    private String holdMember(UUID matchId, UUID seatId) {
        return matchId + ":" + seatId;
    }

    @Override
    public HoldResult hold(UUID matchId, UUID seatId, SeatHold value, int alreadyReserved, int maxCap) {
        try {
            String json = objectMapper.writeValueAsString(value);
            String member = holdMember(matchId, seatId);
            double expiryScore = Instant.now().getEpochSecond() + PAYMENT_WINDOW_SECONDS;

            Long result = redisTemplate.execute(
                    HOLD_SCRIPT,
                    List.of(
                            seatKey(matchId, seatId),
                            holdSetKey(value.reservationId()),
                            HOLD_EXPIRY_INDEX_KEY
                    ),
                    String.valueOf(alreadyReserved),
                    String.valueOf(maxCap),
                    json,
                    String.valueOf(HOLD_TTL.getSeconds()),
                    member,
                    String.valueOf(expiryScore),
                    member
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
                            HOLD_EXPIRY_INDEX_KEY
                    ),
                    reservedValue.reservationId().toString(),
                    json,
                    String.valueOf(ttl.getSeconds()),
                    holdMember(matchId, seatId)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[Redis] 좌석 confirm(HOLD|EXPIRE_PENDING→RESERVED) 실패 - matchId={}, seatId={}",
                    matchId, seatId, e);
            throw new InternalServerException("Redis 좌석 확정 처리 중 오류가 발생했습니다.");
        }
    }

    @Override
    public void upsertReserved(UUID matchId, UUID seatId, SeatHold reservedValue, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(reservedValue);
            redisTemplate.opsForValue().set(seatKey(matchId, seatId), json, ttl);
            redisTemplate.opsForZSet().remove(HOLD_EXPIRY_INDEX_KEY, holdMember(matchId, seatId));
        } catch (Exception e) {
            log.error("[Redis] RESERVED upsert 실패 - matchId={}, seatId={}", matchId, seatId, e);
            throw new InternalServerException("Redis RESERVED upsert 실패");
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
            throw new InternalServerException("Redis 좌석 선점 조회 중 오류가 발생했습니다.");
        }
    }

    @Override
    public void release(UUID matchId, UUID seatId) {
        try {
            String json = redisTemplate.opsForValue().get(seatKey(matchId, seatId));
            if (json != null) {
                SeatHold hold = objectMapper.readValue(json, SeatHold.class);
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

    @Override
    public boolean releaseIfOwnedBy(UUID matchId, UUID seatId, UUID reservationId, UUID userId) {
        try {
            Long result = redisTemplate.execute(
                    RELEASE_IF_OWNED_SCRIPT,
                    List.of(
                            seatKey(matchId, seatId),
                            holdSetKey(reservationId)
                    ),
                    reservationId.toString(),
                    userId.toString(),
                    holdMember(matchId, seatId)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.warn("[Redis] 소유권 조건부 락 해제 실패 — TTL 자연 만료 대기. matchId={}, seatId={}",
                    matchId, seatId, e);
            return false;
        }
    }

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
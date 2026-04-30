package org.ticketing.reservation.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.ticketing.common.exception.InternalServerException;
import org.ticketing.reservation.domain.model.redis.SeatHold;
import org.ticketing.reservation.domain.service.SeatHoldRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatHoldRepository implements SeatHoldRepository {

    private static final String KEY_PREFIX = "seat:";
    private static final Duration TTL = Duration.ofSeconds(600);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private String key(UUID matchId, UUID seatId) {
        return KEY_PREFIX + matchId + ":" + seatId;
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

    // Key: holds:{reservationId} → Set of seatIds
    private String holdSetKey(UUID reservationId) {
        return "holds:" + reservationId;
    }

    @Override
    public boolean hold(UUID matchId, UUID seatId, SeatHold value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key(matchId, seatId), json, TTL);

            if (Boolean.TRUE.equals(success)) {
                // ✅ 예약별 HOLD Set에 추가
                redisTemplate.opsForSet().add(holdSetKey(value.reservationId()), seatId.toString());
                redisTemplate.expire(holdSetKey(value.reservationId()), TTL);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("[Redis] 좌석 선점 실패", e);
            throw new InternalServerException("Redis 좌석 선점 실패");
        }
    }

    @Override
    public void release(UUID matchId, UUID seatId) {
        try {
            String json = redisTemplate.opsForValue().get(key(matchId, seatId));
            if (json != null) {
                SeatHold hold = objectMapper.readValue(json, SeatHold.class);
                // ✅ 예약별 HOLD Set에서 제거
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
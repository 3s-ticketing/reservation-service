package org.ticketing.reservation.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
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
    public boolean hold(UUID matchId, UUID seatId, SeatHold value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key(matchId, seatId), json, TTL);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.error("[Redis] 좌석 선점 실패 - matchId={}, seatId={}", matchId, seatId, e);
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
        redisTemplate.delete(key(matchId, seatId));
    }
}
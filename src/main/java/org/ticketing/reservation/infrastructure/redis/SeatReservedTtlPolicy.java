package org.ticketing.reservation.infrastructure.redis;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RESERVED Redis 락의 TTL 정책.
 *
 * <p>티켓 오픈 시점까지 RESERVED 락을 유지한다.
 *
 * <p>{@link MatchTicketWindowCache} 에 ticketOpenAt 이 없으면 fallback 으로 30일 사용.
 * (match.approved 이벤트가 아직 수신 안 된 경우 등.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReservedTtlPolicy {

    /** 티켓 오픈 시각 정보가 없을 때 사용할 기본 TTL. */
    private static final Duration FALLBACK_TTL = Duration.ofDays(30);

    /** 너무 짧은 TTL 을 막기 위한 하한 (시계 스큐 / 직전 confirm 등 대비). */
    private static final Duration MIN_TTL = Duration.ofMinutes(10);

    private final MatchTicketWindowCache cache;

    /**
     * 매치의 RESERVED TTL 계산.
     *
     * @return 현재 시각 ~ ticketOpenAt 까지의 Duration. 캐시 미스 시 {@link #FALLBACK_TTL}.
     */
    public Duration ttlFor(UUID matchId) {
        return cache.getTicketOpenAt(matchId)
                .map(this::durationUntil)
                .orElseGet(() -> {
                    log.warn("[TTL] ticketOpenAt 캐시 미스 — fallback TTL 사용. matchId={}", matchId);
                    return FALLBACK_TTL;
                });
    }

    private Duration durationUntil(OffsetDateTime ticketOpenAt) {
        Duration d = Duration.between(OffsetDateTime.now(), ticketOpenAt);
        if (d.compareTo(MIN_TTL) < 0) {
            return MIN_TTL;
        }
        return d;
    }
}

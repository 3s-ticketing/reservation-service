package org.ticketing.reservation.infrastructure.redis;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Match 별 티켓 오픈 시각 캐시.
 *
 * <p>RESERVED 락의 TTL 을 계산할 때 참조한다.
 *
 * <p>TODO: 실제 운영에서는 match-service 에서 발행되는 {@code match.approved}
 * 이벤트(ticketOpenAt 포함)를 Kafka 컨슈머로 받아 본 캐시에 적재한다.
 * 이번 구현 단계에서는 이벤트 수신 로직은 따로 만들지 않고 캐시 진입점만 둔다.
 *
 * <pre>{@code
 * @KafkaListener(topics = "match.approved")
 * public void onMatchApproved(MatchApprovedEvent e) {
 *     cache.register(e.matchId(), e.ticketOpenAt());
 * }
 * }</pre>
 *
 * <p>이벤트 수신 전에는 {@link #getTicketOpenAt} 가 빈 Optional 을 반환하며,
 * {@link SeatReservedTtlPolicy} 가 fallback TTL 을 사용한다.
 */
@Component
public class MatchTicketWindowCache {

    private final ConcurrentMap<UUID, OffsetDateTime> ticketOpenByMatchId = new ConcurrentHashMap<>();

    /** TTL 정책에서 호출 — 매치의 티켓 오픈 시각 조회. */
    public Optional<OffsetDateTime> getTicketOpenAt(UUID matchId) {
        return Optional.ofNullable(ticketOpenByMatchId.get(matchId));
    }

    /**
     * match.approved 이벤트 수신 시 호출.
     * TODO: 실제 컨슈머 구현 시 본 메서드 호출.
     */
    public void register(UUID matchId, OffsetDateTime ticketOpenAt) {
        ticketOpenByMatchId.put(matchId, ticketOpenAt);
    }

    /** 매치가 종료/취소되어 더 이상 점유 정보가 필요 없을 때 호출. */
    public void evict(UUID matchId) {
        ticketOpenByMatchId.remove(matchId);
    }
}

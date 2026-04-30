package org.ticketing.reservation.domain.model.redis;

/**
 * Redis 좌석 락 상태.
 *
 * <ul>
 *   <li>{@link #HOLD} — 사용자 선점, 짧은 TTL (10분)</li>
 *   <li>{@link #RESERVED} — 결제 확정 후 영속 점유, ticketOpenAt 기반 긴 TTL</li>
 * </ul>
 *
 * <p>두 상태는 같은 Redis 키를 공유한다 — payload 의 {@code state} 필드로 구분.
 * 이렇게 단일 키로 두 상태를 보호함으로써 hold 시도 시 한 번의 SETNX 만으로
 * HOLD/RESERVED 어느 상태든 차단할 수 있다.
 */
public enum SeatLockStatus {
    HOLD,
    RESERVED
}

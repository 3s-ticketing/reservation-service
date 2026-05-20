package org.ticketing.reservation.domain.model.redis;

/**
 * Redis 좌석 락 상태.
 *
 * <ul>
 *   <li>{@link #HOLD} — 사용자 선점, TTL 660초 (결제 10분 + 유예 1분)</li>
 *   <li>{@link #EXPIRE_PENDING} — 결제 윈도우(10분) 초과 감지, TTL 자연 만료(최대 1분) 대기.
 *       스케줄러가 {@code hold_expiry_index} Sorted Set을 통해 HOLD→EXPIRE_PENDING 전이 후
 *       payment-service에 결제 상태를 조회해 confirm 또는 expire 처리한다.</li>
 *   <li>{@link #RESERVED} — 결제 확정 후 영속 점유, ticketOpenAt 기반 긴 TTL</li>
 * </ul>
 *
 * <p>세 상태는 같은 Redis 키를 공유한다 — payload 의 {@code state} 필드로 구분.
 * 이렇게 단일 키로 모든 상태를 보호함으로써 hold 시도 시 한 번의 SETNX 만으로
 * 어느 상태든 차단할 수 있다.
 */
public enum SeatLockStatus {
    HOLD,
    EXPIRE_PENDING,
    RESERVED
}

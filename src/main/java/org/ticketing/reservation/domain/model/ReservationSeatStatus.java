package org.ticketing.reservation.domain.model;

/**
 * DB 에 영속되는 좌석 상태.
 *
 * <p>실시간 점유(HOLD)는 Redis 가 단독 관리하므로 DB 행은 항상 결제 확정(RESERVED)
 * 시점에야 생성된다. 따라서 본 enum 은 두 종착 상태만 가진다:
 *
 * <ul>
 *   <li>{@link #RESERVED} — 결제 확정. 매치 종료 시점까지 유지.</li>
 *   <li>{@link #CANCELED} — 사용자/시스템 취소. 종착 상태.</li>
 * </ul>
 *
 * <p>Redis 측 상태(HOLD / RESERVED)는 {@code SeatLockStatus} 참고.
 */
public enum ReservationSeatStatus {
    RESERVED,
    CANCELED;

    public boolean canTransitionTo(ReservationSeatStatus target) {
        if (target == null || target == this) return false;
        return switch (this) {
            case RESERVED -> target == CANCELED;
            case CANCELED -> false;
        };
    }

    public boolean isActive() {
        return this == RESERVED;
    }
}

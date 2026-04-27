package org.ticketing.reservationseat.domain.model;

/**
 * 예약 좌석 상태.
 *
 * <p>전이 규칙:
 * <ul>
 *   <li>HOLD     → RESERVED (결제 완료) / EXPIRED (TTL 만료) / CANCELED (사용자 취소)</li>
 *   <li>RESERVED → CANCELED (환불·관리 취소)</li>
 *   <li>AVAILABLE 는 외부 시스템 좌석 상태와의 호환을 위해 정의되며 본 테이블에서는 통상 발생하지 않는다.</li>
 *   <li>EXPIRED, CANCELED 는 종착 상태</li>
 * </ul>
 */
public enum ReservationSeatStatus {
    AVAILABLE,
    HOLD,
    RESERVED,
    EXPIRED,
    CANCELED;

    public boolean canTransitionTo(ReservationSeatStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case HOLD     -> target == RESERVED || target == EXPIRED || target == CANCELED;
            case RESERVED -> target == CANCELED;
            case AVAILABLE, EXPIRED, CANCELED -> false;
        };
    }

    public boolean isActive() {
        return this == HOLD || this == RESERVED;
    }
}

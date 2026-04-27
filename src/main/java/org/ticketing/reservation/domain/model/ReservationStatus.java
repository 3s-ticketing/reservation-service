package org.ticketing.reservation.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 예매 상태.
 *
 * <p>전이 규칙:
 * <ul>
 *   <li>PENDING   → COMPLETED / EXPIRED / CANCELLED</li>
 *   <li>COMPLETED → CANCELLED (결제 취소·환불)</li>
 *   <li>EXPIRED, CANCELLED 는 종착 상태</li>
 * </ul>
 */
public enum ReservationStatus {
    PENDING,
    COMPLETED,
    EXPIRED,
    CANCELLED;

    private static final Set<ReservationStatus> CANCELLABLE =
            EnumSet.of(PENDING, COMPLETED);

    public boolean canTransitionTo(ReservationStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING   -> target == COMPLETED || target == EXPIRED || target == CANCELLED;
            case COMPLETED -> target == CANCELLED;
            case EXPIRED, CANCELLED -> false;
        };
    }

    public boolean isCancellable() {
        return CANCELLABLE.contains(this);
    }

    public boolean isTerminal() {
        return this == EXPIRED || this == CANCELLED;
    }
}

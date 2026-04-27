package org.ticketing.ticket.domain.model;

/**
 * 티켓 사용 상태.
 *
 * <ul>
 *   <li>AVAILABLE: 사용 가능 (발급 직후 기본값)</li>
 *   <li>USED:      사용 완료 (입장 처리 됨)</li>
 *   <li>CANCELED:  사용 불가 (예매 취소·환불에 의해 무효화)</li>
 * </ul>
 *
 * <p>USED, CANCELED 는 종착 상태이며 추가 전이를 허용하지 않는다.
 */
public enum TicketStatus {
    AVAILABLE,
    USED,
    CANCELED;

    public boolean canTransitionTo(TicketStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case AVAILABLE -> target == USED || target == CANCELED;
            case USED, CANCELED -> false;
        };
    }
}

package org.ticketing.reservation.domain.event.payload;

/**
 * 예매 취소 사유.
 *
 * <ul>
 *   <li>{@link #USER_CANCEL} — 사용자 또는 결제 환불에 의한 취소</li>
 *   <li>{@link #EXPIRED} — 결제 미완료로 인한 만료 취소</li>
 *   <li>{@link #MATCH_CANCELED} — 경기 취소에 의한 일괄 취소</li>
 * </ul>
 */
public enum CancelReason {
    USER_CANCEL,
    EXPIRED,
    MATCH_CANCELED
}

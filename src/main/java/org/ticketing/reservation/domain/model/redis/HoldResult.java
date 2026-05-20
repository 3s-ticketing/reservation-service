package org.ticketing.reservation.domain.model.redis;

/**
 * {@code SeatHoldRepository.hold()} 의 원자적 선점 결과.
 *
 * <ul>
 *   <li>{@link #SUCCESS}       — 선점 성공. HOLD 키 생성 + holds set 갱신 완료.</li>
 *   <li>{@link #SEAT_TAKEN}    — 이미 다른 사용자(또는 예매)가 해당 좌석을 점유 중.</li>
 *   <li>{@link #CAP_EXCEEDED}  — 예매당 최대 좌석 수 초과. 신규 HOLD 불가.</li>
 * </ul>
 */
public enum HoldResult {
    SUCCESS,
    SEAT_TAKEN,
    CAP_EXCEEDED
}

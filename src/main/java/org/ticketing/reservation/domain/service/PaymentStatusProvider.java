package org.ticketing.reservation.domain.service;

import java.util.UUID;

/**
 * 결제 상태 조회 포트.
 *
 * <p>payment-service 에 결제 상태를 조회하기 위한 도메인 인터페이스.
 * 인프라 구현(Feign 등)은 추후 작성한다. 현재는 {@link #STUB} 구현이 사용된다.
 *
 * <h3>사용 시점</h3>
 * <p>{@code HoldExpiryScheduler} 가 HOLD TTL(10분)이 경과한 좌석을 EXPIRE_PENDING 으로
 * 전이한 뒤, 결제 완료 여부를 확인하기 위해 호출한다.
 */
public interface PaymentStatusProvider {

    /**
     * 주어진 예매 ID({@code orderId})에 연결된 결제의 현재 상태를 반환한다.
     *
     * @param reservationId 예매 ID (payment-service 에서는 orderId 로 참조)
     * @return 결제 상태. 조회 실패 또는 미구현 시 {@link PaymentStatus#UNKNOWN} 반환.
     */
    PaymentStatus getPaymentStatus(UUID reservationId);

    /**
     * 결제 상태.
     *
     * <ul>
     *   <li>{@link #COMPLETED} — 결제 완료: 예매 confirm 진행</li>
     *   <li>{@link #FAILED}    — 결제 실패: 예매 expire 처리</li>
     *   <li>{@link #PENDING}   — 결제 진행 중: 유예 시간 내 자연 처리 대기</li>
     *   <li>{@link #UNKNOWN}   — 조회 실패 / 미구현: 로그 기록 후 TTL 자연 만료 대기</li>
     * </ul>
     */
    enum PaymentStatus {
        COMPLETED,
        FAILED,
        PENDING,
        UNKNOWN
    }

    /**
     * Feign 미구현 시 사용하는 Stub 구현.
     *
     * <p>항상 {@link PaymentStatus#UNKNOWN} 을 반환하므로 스케줄러가 TTL 자연 만료에 위임한다.
     * 추후 Feign 클라이언트 구현으로 교체한다.
     */
    PaymentStatusProvider STUB = reservationId -> PaymentStatus.UNKNOWN;
}

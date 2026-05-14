package org.ticketing.reservation.infrastructure.client;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.ticketing.reservation.domain.service.PaymentStatusProvider;
import org.ticketing.reservation.infrastructure.client.dto.PaymentStatusResponse;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class PaymentStatusProviderImpl implements PaymentStatusProvider {

    private final PaymentStatusClient paymentStatusClient;

    /**
     * 예매 ID 에 연결된 가장 최근 결제 시도의 상태를 반환한다.
     *
     * <h3>payment-service PaymentStatus → PaymentStatusProvider.PaymentStatus 매핑</h3>
     * <ul>
     *   <li>SUCCESS                      → COMPLETED  (예매 confirm 트리거)</li>
     *   <li>REFUNDING · REFUNDED         → COMPLETED  (결제 완료 후 환불 진행 중/완료)</li>
     *   <li>FAIL · EXPIRED               → FAILED     (예매 expire 트리거)</li>
     *   <li>INIT · PAYING                → PENDING    (결제 진행 중 — 유예)</li>
     *   <li>결제 내역 없음(404) · 기타 오류 → UNKNOWN   (로그 후 TTL 만료 대기)</li>
     * </ul>
     */
    @Override
    public PaymentStatus getPaymentStatus(UUID reservationId) {
        try {
            PaymentStatusResponse response = paymentStatusClient.getLatestPayment(reservationId).data();
            return switch (response.status()) {
                case "SUCCESS", "REFUNDING", "REFUNDED" -> PaymentStatus.COMPLETED;
                case "FAIL", "EXPIRED"                  -> PaymentStatus.FAILED;
                case "INIT", "PAYING"                   -> PaymentStatus.PENDING;
                default -> {
                    log.warn("[PaymentStatusProvider] 알 수 없는 결제 상태 — reservationId={}, status={}",
                            reservationId, response.status());
                    yield PaymentStatus.UNKNOWN;
                }
            };
        } catch (Exception e) {
            log.warn("[PaymentStatusProvider] 결제 상태 조회 실패 — reservationId={}, cause={}",
                    reservationId, e.getMessage());
            return PaymentStatus.UNKNOWN;
        }
    }
}
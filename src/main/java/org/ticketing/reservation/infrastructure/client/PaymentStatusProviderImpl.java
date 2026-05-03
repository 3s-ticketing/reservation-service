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

    @Override
    public PaymentStatus getPaymentStatus(UUID reservationId) {
        try {
            PaymentStatusResponse response = paymentStatusClient.getPaymentStatus(reservationId);
            return response.status();
        } catch (Exception e) {
            log.error("[PaymentStatusProvider] 결제 상태 조회 실패 - reservationId: {}", reservationId, e);
            return PaymentStatus.UNKNOWN;
        }
    }
}
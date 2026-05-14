package org.ticketing.reservation.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.common.response.CommonResponse;
import org.ticketing.reservation.infrastructure.client.dto.PaymentStatusResponse;

@FeignClient(name = "payment-service", url = "${services.payment.url}")
public interface PaymentStatusClient {

    /**
     * 예매 ID 기준 가장 최근 결제 시도를 조회한다.
     *
     * <p>결제 내역이 없으면 payment-service 가 404 를 반환하고
     * 호출 측({@link PaymentStatusProviderImpl})에서 Exception 을 catch 해 UNKNOWN 으로 처리한다.
     */
    @GetMapping("/internal/payments/reservations/{reservationId}/latest")
    CommonResponse<PaymentStatusResponse> getLatestPayment(@PathVariable UUID reservationId);

}
package org.ticketing.reservation.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.common.response.CommonResponse;
import org.ticketing.reservation.infrastructure.client.dto.PaymentStatusResponse;

@FeignClient(name = "payment-service")
public interface PaymentStatusClient {

    @GetMapping("/internal/payments/reservations/{reservationId}/success")
    CommonResponse<PaymentStatusResponse> getPaymentStatus(@PathVariable UUID reservationId);

}
package org.ticketing.reservation.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.reservation.infrastructure.client.dto.PaymentStatusResponse;

@FeignClient(name = "payment-service", url = "${feign.payment-service.url}")
public interface PaymentStatusClient {

    @GetMapping("/internal/payments/{reservationId}/status")
    PaymentStatusResponse getPaymentStatus(@PathVariable UUID reservationId);
}
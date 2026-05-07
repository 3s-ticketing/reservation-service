package org.ticketing.ticket.infrastructure.provider;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ticketing.ticket.domain.service.QrProvider;

@Component
public class SimpleQrProvider implements QrProvider {

    @Value("${app.base-url:http://localhost:8082}")
    private String baseUrl;

    @Override
    public String issueQr(UUID reservationId, UUID userId) {
        return baseUrl + "/api/tickets/verify?reservationId=" + reservationId
                + "&userId=" + userId;
    }
}
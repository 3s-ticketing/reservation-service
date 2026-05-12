package org.ticketing.reservation.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.reservation.infrastructure.client.dto.SeatGradePriceResponse;

import java.util.UUID;

@FeignClient(name = "match-service", url = "${services.match.url}")
public interface MatchInternalClient {

    @GetMapping("/internal/matches/{matchId}/seat-grades/{seatGradeId}")
    SeatGradePriceResponse getSeatGradePrice(
            @PathVariable UUID matchId,
            @PathVariable UUID seatGradeId
    );
}
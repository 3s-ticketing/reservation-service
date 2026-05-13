package org.ticketing.reservation.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.common.response.CommonResponse;
import org.ticketing.reservation.infrastructure.client.dto.SeatResponse;

import java.util.UUID;

@FeignClient(name = "seat-service", url = "${services.seat.url}")
public interface SeatInternalClient {

    @GetMapping("/internal/seats/{seatId}")
    CommonResponse<SeatResponse> getSeat(@PathVariable UUID seatId);

}
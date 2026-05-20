package org.ticketing.reservation.infrastructure.provider;

import java.util.UUID;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing.reservation.domain.exception.ExternalServiceUnavailableException;
import org.ticketing.reservation.domain.service.SeatProvider;
import org.ticketing.reservation.infrastructure.client.MatchInternalClient;
import org.ticketing.reservation.infrastructure.client.SeatInternalClient;
import org.ticketing.reservation.infrastructure.client.dto.SeatGradePriceResponse;
import org.ticketing.reservation.infrastructure.client.dto.SeatResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatProviderImpl implements SeatProvider {

    private final SeatInternalClient seatInternalClient;
    private final MatchInternalClient matchInternalClient;

    @Override
    public SeatSnapshot fetchSnapshot(UUID matchId, UUID seatId) {
        SeatResponse seat = getSeatWithCircuitBreaker(seatId);
        SeatGradePriceResponse price = getPriceWithCircuitBreaker(matchId, seat.seatGradeId());

        return new SeatSnapshot(
                seat.seatId(),
                seat.stadiumId(),
                seat.seatGradeId(),
                seat.column() + seat.seatNumber(),
                price.price()
        );
    }

    // ──────────────────────────────────────────
    // seat-service 서킷 브레이커
    // ──────────────────────────────────────────

    @CircuitBreaker(name = "seatService", fallbackMethod = "getSeatFallback")
    public SeatResponse getSeatWithCircuitBreaker(UUID seatId) {
        return seatInternalClient.getSeat(seatId).data();
    }

    private SeatResponse getSeatFallback(UUID seatId, Exception e) {
        log.error("[CircuitBreaker] seat-service 호출 실패 — seatId={}, cause={}",
                seatId, e.getMessage());
        throw new ExternalServiceUnavailableException(
                "좌석 정보 조회가 일시적으로 불가합니다. 잠시 후 다시 시도해 주세요.");
    }

    // ──────────────────────────────────────────
    // match-service 서킷 브레이커
    // ──────────────────────────────────────────

    @CircuitBreaker(name = "matchService", fallbackMethod = "getPriceFallback")
    public SeatGradePriceResponse getPriceWithCircuitBreaker(UUID matchId, UUID seatGradeId) {
        return matchInternalClient.getSeatGradePrice(matchId, seatGradeId).data();
    }

    private SeatGradePriceResponse getPriceFallback(UUID matchId, UUID seatGradeId, Exception e) {
        log.error("[CircuitBreaker] match-service 호출 실패 — matchId={}, seatGradeId={}, cause={}",
                matchId, seatGradeId, e.getMessage());
        throw new ExternalServiceUnavailableException(
                "가격 정보 조회가 일시적으로 불가합니다. 잠시 후 다시 시도해 주세요.");
    }
}
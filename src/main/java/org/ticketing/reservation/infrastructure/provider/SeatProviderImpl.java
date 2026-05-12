package org.ticketing.reservation.infrastructure.provider;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ticketing.reservation.domain.service.SeatProvider;
import org.ticketing.reservation.infrastructure.client.MatchInternalClient;
import org.ticketing.reservation.infrastructure.client.SeatInternalClient;
import org.ticketing.reservation.infrastructure.client.dto.SeatGradePriceResponse;
import org.ticketing.reservation.infrastructure.client.dto.SeatResponse;

@Component
@RequiredArgsConstructor
public class SeatProviderImpl implements SeatProvider {

    private final SeatInternalClient seatInternalClient;
    private final MatchInternalClient matchInternalClient;

    @Override
    public SeatSnapshot fetchSnapshot(UUID matchId, UUID seatId) {
        SeatResponse seat = seatInternalClient.getSeat(seatId);
        SeatGradePriceResponse price = matchInternalClient.getSeatGradePrice(matchId, seat.seatGradeId());

        return new SeatSnapshot(
                seat.seatId(),
                seat.stadiumId(),
                seat.seatGradeId(),
                seat.column() + seat.seatNumber(),
                price.price()
        );
    }
}
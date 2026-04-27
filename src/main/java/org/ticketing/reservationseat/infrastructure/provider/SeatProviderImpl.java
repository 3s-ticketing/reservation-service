package org.ticketing.reservationseat.infrastructure.provider;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ticketing.reservationseat.domain.service.SeatProvider;

@Component
@RequiredArgsConstructor
public class SeatProviderImpl implements SeatProvider {

    // TODO: Feign 연동 시 주입
    // private final SeatClient seatClient;

    @Override
    public boolean existsAndUsable(UUID matchId, UUID seatId) {
        // TODO: Feign - seatClient.existsAndUsable(matchId, seatId)
        return true; // 로컬 개발용 임시값
    }

    @Override
    public SeatSnapshot fetchSnapshot(UUID matchId, UUID seatId) {
        // TODO: Feign - seatClient.getSeat(seatId)
        return new SeatSnapshot(
                seatId,
                UUID.randomUUID(), // stadiumId
                UUID.randomUUID(), // seatGradeId
                "A1",             // seatNumber
                10000L            // price
        );
    }
}
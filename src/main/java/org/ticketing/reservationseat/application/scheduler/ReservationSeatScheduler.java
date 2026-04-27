package org.ticketing.reservationseat.application.scheduler;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.reservationseat.domain.event.ReservationSeatEventPublisher;
import org.ticketing.reservationseat.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservationseat.domain.model.entity.ReservationSeat;
import org.ticketing.reservationseat.domain.model.enums.ReservationSeatStatus;
import org.ticketing.reservationseat.infrastructure.repository.ReservationSeatRepositoryImpl;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationSeatScheduler {

    private static final int HOLD_EXPIRE_MINUTES = 10;

    private final ReservationSeatRepositoryImpl reservationSeatRepository;
    private final ReservationSeatEventPublisher eventPublisher;

    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void expireHeldSeats() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(HOLD_EXPIRE_MINUTES);
        List<ReservationSeat> expiredSeats = reservationSeatRepository.findHeldOlderThan(threshold);

        if (expiredSeats.isEmpty()) {
            return;
        }

        OffsetDateTime releasedAt = OffsetDateTime.now();

        expiredSeats.forEach(seat -> {
            seat.expire();

            // EXPIRED → 좌석 해제 이벤트 발행 (외부 좌석 인벤토리 가용 상태로 복구 트리거)
            eventPublisher.publishReleased(new ReservationSeatReleasedEvent(
                    seat.getId(),
                    seat.getReservationId(),
                    seat.getMatchId(),
                    seat.getSeatId(),
                    ReservationSeatStatus.EXPIRED,  // reason
                    releasedAt
            ));
        });

        log.info("[스케줄러] HOLD 만료 처리 완료 - 처리 건수: {}", expiredSeats.size());
    }
}
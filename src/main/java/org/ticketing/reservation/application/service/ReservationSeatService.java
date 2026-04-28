package org.ticketing.reservation.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.reservation.application.dto.command.CreateReservationSeatCommand;
import org.ticketing.reservationseat.application.dto.result.ReservationSeatResult;
import org.ticketing.reservation.domain.event.ReservationSeatEventPublisher;
import org.ticketing.reservationseat.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservationseat.domain.model.entity.ReservationSeat;
import org.ticketing.reservationseat.domain.service.SeatProvider;
import org.ticketing.reservation.infrastructure.repository.ReservationSeatRepositoryImpl;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationSeatService {

    private final ReservationSeatRepositoryImpl reservationSeatRepository;
    private final SeatProvider seatProvider;
    private final ReservationSeatEventPublisher eventPublisher;

    // TODO: Feign 연동 시 Provider 추가
    // private final ReservationProvider reservationProvider;
    // private final MatchProvider matchProvider;

    // ───────────────── Command ─────────────────

    @Transactional
    public ReservationSeatResult createReservationSeat(CreateReservationSeatCommand command) {

        // TODO: ReservationProvider - 예약 조회 및 검증
        UUID matchId = UUID.randomUUID(); // reservation.getMatchId()

        // 좌석 존재 및 사용 가능 여부
        if (!seatProvider.existsAndUsable(matchId, command.seatId())) {
            throw new IllegalArgumentException("유효하지 않은 좌석입니다.");
        }

        // 좌석 정보 스냅샷 조회
        SeatProvider.SeatSnapshot snapshot = seatProvider.fetchSnapshot(matchId, command.seatId());

        // 구매 제한
        int currentCount = reservationSeatRepository.countActiveByReservationId(command.reservationId());
        if (currentCount >= 4) {
            throw new IllegalStateException("예약 가능한 최대 좌석 수를 초과하였습니다.");
        }

        // 중복 선점 방지
        reservationSeatRepository.findHoldOrReservedSeat(command.seatId(), matchId)
                .ifPresent(rs -> {
                    throw new IllegalStateException("이미 선점된 좌석입니다.");
                });

        // 예약 좌석 생성
        ReservationSeat reservationSeat = ReservationSeat.hold(
                command.reservationId(),
                matchId,
                snapshot.stadiumId(),
                snapshot.seatId(),
                snapshot.seatGradeId(),
                snapshot.seatNumber(),
                snapshot.price()
        );

        ReservationSeat saved = reservationSeatRepository.saveAndFlush(reservationSeat);

        // HOLD 이벤트 발행
        eventPublisher.publishHeld(new ReservationSeatHeldEvent(
                saved.getId(),
                saved.getReservationId(),
                saved.getMatchId(),
                saved.getSeatId(),
                saved.getPrice(),
                OffsetDateTime.now()
        ));

        return ReservationSeatResult.from(saved);
    }
    //쿼리
    public ReservationSeatResult getReservationSeat(UUID id) {
        ReservationSeat seat = reservationSeatRepository.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("예약 좌석을 찾을 수 없습니다."));
        return ReservationSeatResult.from(seat);
    }

    public List<ReservationSeatResult> getReservationSeats(UUID reservationId) {
        return reservationSeatRepository.findAllByReservationId(reservationId)
                .stream()
                .map(ReservationSeatResult::from)
                .toList();
    }
}
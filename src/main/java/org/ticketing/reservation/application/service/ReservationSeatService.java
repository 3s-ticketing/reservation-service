package org.ticketing.reservation.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.reservation.application.dto.command.CancelReservationSeatCommand;
import org.ticketing.reservation.application.dto.command.ConfirmReservationSeatCommand;
import org.ticketing.reservation.application.dto.command.HoldReservationSeatCommand;
import org.ticketing.reservation.application.dto.result.ReservationSeatResult;
import org.ticketing.reservation.domain.event.ReservationSeatEventPublisher;
import org.ticketing.reservation.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReservedEvent;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.domain.exception.ReservationSeatNotFoundException;
import org.ticketing.reservation.domain.exception.SeatAlreadyHeldException;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.model.ReservationSeat;
import org.ticketing.reservation.domain.model.ReservationSeatStatus;
import org.ticketing.reservation.domain.model.redis.SeatHold;
import org.ticketing.reservation.domain.repository.ReservationRepository;
import org.ticketing.reservation.domain.repository.ReservationSeatRepository;
import org.ticketing.reservation.domain.service.SeatHoldRepository;
import org.ticketing.reservation.domain.service.SeatProvider;
import org.ticketing.common.exception.BadRequestException;
import org.ticketing.common.exception.ConflictException;
import org.ticketing.common.exception.ForbiddenException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationSeatService {

    private static final int MAX_SEAT_PER_RESERVATION = 4;

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final SeatProvider seatProvider;
    private final ReservationSeatEventPublisher eventPublisher;

    @Transactional
    public void holdSeat(HoldReservationSeatCommand command) {

        Reservation reservation = reservationRepository.findActiveById(command.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

        UUID matchId = reservation.getMatchId();

        if (!seatProvider.existsAndUsable(matchId, command.seatId())) {
            throw new BadRequestException("유효하지 않은 좌석입니다.");
        }

        reservationSeatRepository.findActiveByMatchIdAndSeatId(matchId, command.seatId())
                .ifPresent(rs -> {
                    throw new SeatAlreadyHeldException(matchId, command.seatId());
                });

        SeatHold seatHold = new SeatHold(
                command.reservationId(),
                command.userId(),
                matchId,
                command.seatId(),
                OffsetDateTime.now().plusSeconds(600)
        );

        boolean held = seatHoldRepository.hold(matchId, command.seatId(), seatHold);
        if (!held) {
            throw new SeatAlreadyHeldException(matchId, command.seatId());
        }

        eventPublisher.publishHeld(new ReservationSeatHeldEvent(
                null,
                command.reservationId(),
                matchId,
                command.seatId(),
                null,
                OffsetDateTime.now()
        ));
    }

    @Transactional
    public ReservationSeatResult confirmReservationSeat(ConfirmReservationSeatCommand command) {

        Reservation reservation = reservationRepository.findActiveById(command.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

        UUID matchId = reservation.getMatchId();

        SeatHold hold = seatHoldRepository.find(matchId, command.seatId())
                .orElseThrow(() -> new ConflictException("선점 정보가 만료되었습니다."));

        if (!hold.isOwnedBy(command.userId(), command.reservationId())) {
            throw new ForbiddenException("해당 좌석의 선점 정보가 일치하지 않습니다.");
        }

        long currentCount = reservation.getSeats().stream()
                .filter(seat -> seat.getSeatStatus().isActive())
                .count();
        if (currentCount >= MAX_SEAT_PER_RESERVATION) {
            throw new BadRequestException("예약 가능한 최대 좌석 수를 초과하였습니다.");
        }

        SeatProvider.SeatSnapshot snapshot = seatProvider.fetchSnapshot(matchId, command.seatId());

        ReservationSeat newSeat = reservation.addSeat(snapshot);
        Reservation saved = reservationRepository.saveAndFlush(reservation);

        ReservationSeat savedSeat = saved.getSeats().stream()
                .filter(s -> s.getSeatId().equals(command.seatId()))
                .findFirst()
                .orElse(newSeat);

        try {
            seatHoldRepository.release(matchId, command.seatId());
        } catch (Exception e) {
            log.warn("[Redis] 선점 해제 실패 - TTL 자연 만료 대기 matchId={}, seatId={}",
                    matchId, command.seatId(), e);
        }

        eventPublisher.publishReserved(new ReservationSeatReservedEvent(
                savedSeat.getId(),
                saved.getId(),
                matchId,
                savedSeat.getSeatId(),
                OffsetDateTime.now()
        ));

        return ReservationSeatResult.from(savedSeat);
    }

    @Transactional
    public ReservationSeatResult cancelReservationSeat(CancelReservationSeatCommand command) {

        ReservationSeat seat = reservationSeatRepository.findActiveById(command.reservationSeatId())
                .orElseThrow(() -> new ReservationSeatNotFoundException(command.reservationSeatId()));

        Reservation reservation = seat.getReservation();

        ReservationSeat canceled = reservation.releaseSeat(command.reservationSeatId());
        reservation.recalculateTotalPrice();
        reservationRepository.save(reservation);

        eventPublisher.publishReleased(new ReservationSeatReleasedEvent(
                canceled.getId(),
                reservation.getId(),
                reservation.getMatchId(),
                canceled.getSeatId(),
                ReservationSeatStatus.CANCELED,
                OffsetDateTime.now()
        ));

        return ReservationSeatResult.from(canceled);
    }

    @Transactional
    public void releaseHold(UUID matchId, UUID seatId) {
        seatHoldRepository.release(matchId, seatId);
    }

    public ReservationSeatResult getReservationSeat(UUID reservationSeatId) {
        ReservationSeat seat = reservationSeatRepository.findActiveById(reservationSeatId)
                .orElseThrow(() -> new ReservationSeatNotFoundException(reservationSeatId));
        return ReservationSeatResult.from(seat);
    }

    public List<ReservationSeatResult> getReservationSeats(UUID reservationId) {
        return reservationSeatRepository.findAllByReservationId(reservationId)
                .stream()
                .map(ReservationSeatResult::from)
                .toList();
    }
}
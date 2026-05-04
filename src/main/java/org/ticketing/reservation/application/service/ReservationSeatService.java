package org.ticketing.reservation.application.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import org.ticketing.reservation.domain.model.redis.HoldResult;
import org.ticketing.reservation.domain.model.redis.SeatHold;
import org.ticketing.reservation.domain.repository.ReservationRepository;
import org.ticketing.reservation.domain.repository.ReservationSeatRepository;
import org.ticketing.reservation.domain.service.SeatHoldRepository;
import org.ticketing.reservation.domain.service.SeatProvider;
import org.ticketing.reservation.infrastructure.redis.SeatReservedTtlPolicy;
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
    private final SeatReservedTtlPolicy reservedTtlPolicy;
    private final SeatProvider seatProvider;
    private final ReservationSeatEventPublisher eventPublisher;

    @Transactional
    public void holdSeat(HoldReservationSeatCommand command) {

        Reservation reservation = reservationRepository.findActiveById(command.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

        UUID matchId = reservation.getMatchId();

        int reservedCount = (int) reservation.getSeats().stream()
                .filter(seat -> seat.getSeatStatus().isActive())
                .count();

        if (!seatProvider.existsAndUsable(matchId, command.seatId())) {
            throw new BadRequestException("유효하지 않은 좌석입니다.");
        }

        reservationSeatRepository.findActiveByMatchIdAndSeatId(matchId, command.seatId())
                .ifPresent(rs -> {
                    throw new SeatAlreadyHeldException(matchId, command.seatId());
                });

        SeatHold seatHold = SeatHold.hold(
                command.reservationId(),
                command.userId(),
                matchId,
                command.seatId(),
                OffsetDateTime.now().plusSeconds(660)
        );

        HoldResult holdResult = seatHoldRepository.hold(
                matchId, command.seatId(), seatHold, reservedCount, MAX_SEAT_PER_RESERVATION);
        switch (holdResult) {
            case CAP_EXCEEDED -> throw new BadRequestException("예약 가능한 최대 좌석 수를 초과하였습니다.");
            case SEAT_TAKEN   -> throw new SeatAlreadyHeldException(matchId, command.seatId());
            case SUCCESS      -> { /* 정상 흐름 */ }
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
        reservation.recalculateTotalPrice();
        Reservation saved = reservationRepository.saveAndFlush(reservation);

        ReservationSeat savedSeat = saved.getSeats().stream()
                .filter(s -> s.getSeatId().equals(command.seatId()))
                .findFirst()
                .orElse(newSeat);

        Duration reservedTtl = reservedTtlPolicy.ttlFor(matchId);
        SeatHold reservedPayload = SeatHold.reserved(
                command.reservationId(),
                command.userId(),
                matchId,
                command.seatId(),
                OffsetDateTime.now().plus(reservedTtl)
        );
        boolean confirmed = seatHoldRepository.confirm(
                matchId, command.seatId(), reservedPayload, reservedTtl);
        if (!confirmed) {
            throw new ConflictException(
                    "좌석 선점 정보가 만료되었거나 전이에 실패했습니다. 다시 시도해 주세요.");
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

        // 소유권 검증 — 본인 좌석만 취소 가능
        if (!reservation.getUserId().equals(command.userId())) {
            throw new ForbiddenException("본인의 좌석만 취소할 수 있습니다.");
        }

        UUID matchId = reservation.getMatchId();
        UUID seatId = seat.getSeatId();
        UUID reservationId = reservation.getId();

        ReservationSeat canceled = reservation.releaseSeat(command.reservationSeatId());
        reservation.recalculateTotalPrice();
        reservationRepository.save(reservation);

        // DB tx 커밋 후 Redis 락 해제 — 롤백 시 Redis 가 먼저 풀리는 일을 막는다.
        registerRedisReleaseAfterCommit(matchId, seatId, reservationId, command.userId());

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

    /**
     * Redis 좌석 락 해제를 현재 트랜잭션의 afterCommit 페이즈에 예약한다.
     *
     * <p>소유권 검증 후 삭제(releaseIfOwnedBy)로 다른 사용자의 키를 실수로 삭제하는 것을 방지한다.
     * release 실패는 로그만 남기고 무시 — TTL 자연 만료 또는 reconciliation 잡으로 보완.
     */
    private void registerRedisReleaseAfterCommit(
            UUID matchId, UUID seatId, UUID reservationId, UUID userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        seatHoldRepository.releaseIfOwnedBy(matchId, seatId, reservationId, userId);
                    } catch (Exception e) {
                        log.warn("[Redis] afterCommit 락 해제 실패 — 자연 만료 대기. "
                                + "matchId={}, seatId={}", matchId, seatId, e);
                    }
                }
            });
        } else {
            try {
                seatHoldRepository.releaseIfOwnedBy(matchId, seatId, reservationId, userId);
            } catch (Exception e) {
                log.warn("[Redis] 락 해제 실패 — 자연 만료 대기. matchId={}, seatId={}",
                        matchId, seatId, e);
            }
        }
    }

    @Transactional
    public void releaseHold(UUID matchId, UUID seatId) {
        seatHoldRepository.find(matchId, seatId)
                .ifPresent(hold ->
                        seatHoldRepository.releaseIfOwnedBy(
                                matchId, seatId, hold.reservationId(), hold.userId())
                );
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
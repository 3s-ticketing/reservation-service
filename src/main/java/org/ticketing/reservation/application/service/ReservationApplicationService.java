package org.ticketing.reservation.application.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.reservation.application.dto.command.CancelReservationCommand;
import org.ticketing.reservation.application.dto.command.ConfirmReservationCommand;
import org.ticketing.reservation.application.dto.command.CreateReservationCommand;
import org.ticketing.reservation.application.dto.command.ExpireReservationCommand;
import org.ticketing.reservation.application.dto.command.HoldSeatCommand;
import org.ticketing.reservation.application.dto.command.ReleaseSeatCommand;
import org.ticketing.reservation.application.dto.query.GetMyReservationsQuery;
import org.ticketing.reservation.application.dto.query.GetReservationQuery;
import org.ticketing.reservation.application.dto.result.ReservationResult;
import org.ticketing.reservation.domain.event.ReservationEventPublisher;
import org.ticketing.reservation.domain.event.payload.CancelReason;
import org.ticketing.reservation.domain.event.payload.ReservationCancelledEvent;
import org.ticketing.reservation.domain.exception.InvalidReservationStateException;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.domain.exception.SeatAlreadyHeldException;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.repository.ReservationRepository;
import org.ticketing.reservation.domain.service.SeatProvider;
import org.ticketing.reservation.domain.service.SeatProvider.SeatSnapshot;

/**
 * 예매 어그리게이트(루트 {@code Reservation} + 자식 좌석)에 대한 애플리케이션 서비스.
 *
 * <p>좌석은 항상 루트를 통해서만 추가/해제된다. 외부 좌석 메타 조회는
 * {@link SeatProvider} 를 거쳐 도메인 스냅샷을 받아온다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReservationApplicationService {

    private final ReservationRepository reservationRepository;
    private final SeatProvider seatProvider;
    private final ReservationEventPublisher reservationEventPublisher;

    // ──────────────────────────────────────────
    // 커맨드 — 예매 라이프사이클
    // ──────────────────────────────────────────

    /**
     * 예매 생성.
     *
     * <p>요청된 좌석 ID 들을 외부 좌석 도메인에서 검증·스냅샷화한 뒤,
     * PENDING 예매와 함께 HOLD 좌석을 한 트랜잭션에서 생성한다.
     * {@code totalPrice} 는 좌석 가격 합계로 도메인이 직접 계산한다.
     *
     * <p>동일 경기·좌석에 활성 점유가 이미 존재하면 DB 유니크 인덱스
     * {@code uq_reservation_seat_active} 위반으로 {@link SeatAlreadyHeldException} 으로 변환된다.
     */
    @Transactional
    public ReservationResult create(CreateReservationCommand command) {
        if (command.seatIds() == null || command.seatIds().isEmpty()) {
            throw new InvalidReservationStateException("좌석은 최소 한 좌석 이상 선택해야 합니다.");
        }

        // 1. 외부 좌석 도메인 검증 + 스냅샷 수집
        for (UUID seatId : command.seatIds()) {
            if (!seatProvider.existsAndUsable(command.matchId(), seatId)) {
                throw new InvalidReservationStateException(
                        "사용할 수 없는 좌석입니다. seatId=" + seatId);
            }
        }

        // 2. PENDING 예매 + HOLD 좌석 동시 생성
        Reservation reservation = Reservation.create(command.userId(), command.matchId(), 0L);
        for (UUID seatId : command.seatIds()) {
            SeatSnapshot snapshot = seatProvider.fetchSnapshot(command.matchId(), seatId);
            reservation.addSeat(snapshot);
        }
        reservation.recalculateTotalPrice();

        // 3. 저장 — 자식 좌석은 cascade 로 함께 영속화된다.
        try {
            return ReservationResult.from(reservationRepository.save(reservation));
        } catch (DataIntegrityViolationException e) {
            throw new SeatAlreadyHeldException();
        }
    }

    /**
     * 예매 취소 — 사용자 요청.
     *
     * <p>루트 상태를 CANCELLED 로 전이하면 활성 좌석들도 함께 CANCELED 로 전이된다.
     * 마지막에 소프트 딜리트로 마무리한다.
     */
    @Transactional
    public void cancel(CancelReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.cancel();
        reservation.delete(command.canceledBy());

        // 이벤트 발행
        reservationEventPublisher.publishCancelled(
                new ReservationCancelledEvent(
                        reservation.getId(),
                        reservation.getUserId(),
                        CancelReason.USER_CANCEL
                )
        );
    }

    /**
     * 예매 확정 — 결제 완료 이벤트 수신 시 내부 호출.
     *
     * <p>루트가 COMPLETED 로 전이되며, HOLD 좌석들이 RESERVED 로 함께 전이된다.
     */
    @Transactional
    public ReservationResult confirm(ConfirmReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.complete();
        return ReservationResult.from(reservation);
    }

    /**
     * 예매 만료 — TTL 만료 이벤트 수신 시 내부 호출.
     *
     * <p>루트가 EXPIRED 로 전이되며, HOLD 좌석들이 EXPIRED 로 함께 전이된다.
     */
    @Transactional
    public ReservationResult expire(ExpireReservationCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.expire();

        // 이벤트 발행
        reservationEventPublisher.publishCancelled(
                new ReservationCancelledEvent(
                        reservation.getId(),
                        reservation.getUserId(),
                        CancelReason.EXPIRED
                )
        );

        return ReservationResult.from(reservation);
    }

    // ──────────────────────────────────────────
    // 커맨드 — 좌석 단위 (루트 경유)
    // ──────────────────────────────────────────

    /**
     * 기존 PENDING 예매에 좌석을 한 건 추가한다.
     */
    @Transactional
    public ReservationResult holdSeat(HoldSeatCommand command) {
        Reservation reservation = getActive(command.reservationId());

        if (!seatProvider.existsAndUsable(reservation.getMatchId(), command.seatId())) {
            throw new InvalidReservationStateException(
                    "사용할 수 없는 좌석입니다. seatId=" + command.seatId());
        }

        SeatSnapshot snapshot = seatProvider.fetchSnapshot(
                reservation.getMatchId(), command.seatId());
        reservation.addSeat(snapshot);
        reservation.recalculateTotalPrice();

        try {
            return ReservationResult.from(reservationRepository.save(reservation));
        } catch (DataIntegrityViolationException e) {
            throw new SeatAlreadyHeldException(reservation.getMatchId(), command.seatId());
        }
    }

    /**
     * PENDING 예매에서 좌석 한 건만 사용자 취소.
     *
     * <p>예매 자체는 그대로 두고, 자식 좌석 한 건만 CANCELED 로 전이한다.
     */
    @Transactional
    public ReservationResult releaseSeat(ReleaseSeatCommand command) {
        Reservation reservation = getActive(command.reservationId());
        reservation.releaseSeat(command.seatId());
        reservation.recalculateTotalPrice();
        return ReservationResult.from(reservationRepository.save(reservation));
    }

    // ──────────────────────────────────────────
    // 쿼리
    // ──────────────────────────────────────────

    public ReservationResult findById(GetReservationQuery query) {
        return ReservationResult.from(getActive(query.reservationId()));
    }

    public List<ReservationResult> findMyReservations(GetMyReservationsQuery query) {
        return reservationRepository.findAllByUserId(query.userId())
                .stream()
                .map(ReservationResult::from)
                .toList();
    }

    // ──────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────

    private Reservation getActive(UUID reservationId) {
        return reservationRepository.findActiveById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}

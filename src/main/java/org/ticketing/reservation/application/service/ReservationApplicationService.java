package org.ticketing.reservation.application.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.ticketing.reservation.domain.exception.InvalidReservationStateException;
import org.ticketing.reservation.domain.exception.ReservationNotFoundException;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.repository.ReservationRepository;
import org.ticketing.reservation.domain.service.SeatProvider;
import org.ticketing.reservation.domain.service.SeatProvider.SeatSnapshot;

/**
 * 예매 어그리게이트 오케스트레이션 서비스.
 *
 * <h3>SeatProvider I/O 분리 설계</h3>
 * <p>외부 좌석 도메인 I/O({@link SeatProvider})를 트랜잭션 바깥에서 수행한 뒤,
 * 실제 DB 쓰기는 {@link ReservationWriteService}에 위임한다.
 *
 * <ul>
 *   <li>SeatProvider 호출(Feign) 중 DB 커넥션을 점유하지 않아 커넥션 풀 고갈 위험 없음.</li>
 *   <li>스냅샷 수집 후 쓰기가 여러 건이어도 {@code ReservationWriteService} 의
 *       단일 {@code @Transactional} 로 원자성 보장.</li>
 * </ul>
 *
 * <h3>클래스 레벨 @Transactional 미선언 이유</h3>
 * <p>{@code create}, {@code holdSeat} 는 Feign 호출 구간에서 트랜잭션이 없어야 한다.
 * 클래스 레벨 {@code @Transactional(readOnly=true)} 를 두면 해당 메서드에서도 readOnly
 * 트랜잭션이 시작되어 Feign 호출 중 커넥션이 점유된다. 각 메서드가 필요에 따라
 * {@code @Transactional} 을 명시하거나 {@link ReservationWriteService}에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class ReservationApplicationService {

    private final ReservationRepository reservationRepository;
    private final ReservationWriteService reservationWriteService;
    private final SeatProvider seatProvider;

    // ──────────────────────────────────────────
    // 커맨드 — 예매 라이프사이클
    // ──────────────────────────────────────────

    /**
     * 예매 생성.
     *
     * <ol>
     *   <li>좌석 ID 유효성 검증 + 스냅샷 수집 — SeatProvider I/O, 트랜잭션 없음</li>
     *   <li>PENDING 예매 + HOLD 좌석 영속화 — {@link ReservationWriteService#create} 의
     *       단일 {@code @Transactional}</li>
     * </ol>
     */
    public ReservationResult create(CreateReservationCommand command) {
        if (command.seatIds() == null || command.seatIds().isEmpty()) {
            throw new InvalidReservationStateException("좌석은 최소 한 좌석 이상 선택해야 합니다.");
        }

        // 1. SeatProvider I/O — 트랜잭션 바깥, DB 커넥션 미보유
        List<SeatSnapshot> snapshots = command.seatIds().stream()
                .map(seatId -> {
                    if (!seatProvider.existsAndUsable(command.matchId(), seatId)) {
                        throw new InvalidReservationStateException(
                                "사용할 수 없는 좌석입니다. seatId=" + seatId);
                    }
                    return seatProvider.fetchSnapshot(command.matchId(), seatId);
                })
                .toList();

        // 2. 영속화 — 단일 write 트랜잭션
        return reservationWriteService.create(command, snapshots);
    }

    /**
     * 예매 취소 — 사용자 요청.
     *
     * <p>Feign 호출이 없으므로 바로 위임한다.
     */
    public void cancel(CancelReservationCommand command) {
        reservationWriteService.cancel(command);
    }

    /**
     * 예매 확정 — 결제 완료 이벤트 수신 시 내부 호출.
     */
    public ReservationResult confirm(ConfirmReservationCommand command) {
        return reservationWriteService.confirm(command);
    }

    /**
     * 예매 만료 — TTL 만료 이벤트 수신 시 내부 호출.
     */
    public ReservationResult expire(ExpireReservationCommand command) {
        return reservationWriteService.expire(command);
    }

    // ──────────────────────────────────────────
    // 커맨드 — 좌석 단위 (루트 경유)
    // ──────────────────────────────────────────

    /**
     * 기존 PENDING 예매에 좌석을 한 건 추가한다.
     *
     * <ol>
     *   <li>matchId 조회 — readOnly 트랜잭션, 즉시 반환</li>
     *   <li>SeatProvider I/O — 트랜잭션 바깥</li>
     *   <li>좌석 추가 + 영속화 — write 트랜잭션</li>
     * </ol>
     */
    public ReservationResult holdSeat(HoldSeatCommand command) {
        // 1. matchId 확인 (짧은 readOnly 트랜잭션)
        UUID matchId = reservationWriteService.getMatchId(command.reservationId());

        // 2. SeatProvider I/O — 트랜잭션 바깥
        if (!seatProvider.existsAndUsable(matchId, command.seatId())) {
            throw new InvalidReservationStateException(
                    "사용할 수 없는 좌석입니다. seatId=" + command.seatId());
        }
        SeatSnapshot snapshot = seatProvider.fetchSnapshot(matchId, command.seatId());

        // 3. 영속화 — write 트랜잭션
        return reservationWriteService.holdSeat(command, snapshot);
    }

    /**
     * PENDING 예매에서 좌석 한 건만 사용자 취소.
     *
     * <p>Feign 호출이 없으므로 바로 위임한다.
     */
    public ReservationResult releaseSeat(ReleaseSeatCommand command) {
        return reservationWriteService.releaseSeat(command);
    }

    // ──────────────────────────────────────────
    // 쿼리
    // ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReservationResult findById(GetReservationQuery query) {
        Reservation reservation = reservationRepository.findActiveById(query.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(query.reservationId()));
        return ReservationResult.from(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResult> findMyReservations(GetMyReservationsQuery query) {
        return reservationRepository.findAllByUserId(query.userId())
                .stream()
                .map(ReservationResult::from)
                .toList();
    }
}

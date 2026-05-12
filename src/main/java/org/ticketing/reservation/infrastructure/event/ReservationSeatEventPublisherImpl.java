package org.ticketing.reservation.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ticketing.common.event.Events;
import org.ticketing.reservation.domain.event.ReservationSeatEventPublisher;
import org.ticketing.reservation.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReservedEvent;

/**
 * 좌석 단위 이벤트 Kafka 발행 구현체.
 *
 * <p>Outbox 패턴을 통해 DB 트랜잭션과 동일한 원자성 안에서 이벤트를 등록한다.
 * {@link Events#trigger} 호출 시 {@code OutboxEventListener} 가 같은 트랜잭션에
 * Outbox 레코드를 삽입하고, AFTER_COMMIT 이후 릴레이 스케줄러가 Kafka 로 전송한다.
 *
 * <ul>
 *   <li>{@code reservation.seat.reserved} — 좌석 결제 확정 시 발행, match-service 가 잔여 좌석 수 감소</li>
 *   <li>{@code reservation.seat.released} — 좌석 취소/만료 시 발행, match-service 가 잔여 좌석 수 증가</li>
 * </ul>
 */
@Slf4j
@Component
public class ReservationSeatEventPublisherImpl implements ReservationSeatEventPublisher {

    private static final String DOMAIN_TYPE = "RESERVATION_SEAT";

    @Value("${topics.reservation.seat.reserved:reservation.seat.reserved}")
    private String seatReservedTopic;

    @Value("${topics.reservation.seat.released:reservation.seat.released}")
    private String seatReleasedTopic;

    @Override
    public void publishHeld(ReservationSeatHeldEvent event) {
        // HOLD 이벤트는 외부 서비스 연동 불필요 — 로그만 기록
        log.debug("[이벤트] 좌석 HOLD — seatId={}, matchId={}", event.seatId(), event.matchId());
    }

    @Override
    public void publishReserved(ReservationSeatReservedEvent event) {
        Events.trigger(
                "reservation-seat-reserved-" + event.reservationSeatId(),
                DOMAIN_TYPE,
                event.matchId().toString(),         // matchId 기준 파티션 → 같은 경기 이벤트 순서 보장
                seatReservedTopic,
                event
        );
        log.info("[ReservationSeatReservedEvent] Outbox 등록 — reservationSeatId={}, matchId={}, seatGradeId={}",
                event.reservationSeatId(), event.matchId(), event.seatGradeId());
    }

    @Override
    public void publishReleased(ReservationSeatReleasedEvent event) {
        Events.trigger(
                "reservation-seat-released-" + event.reservationSeatId(),
                DOMAIN_TYPE,
                event.matchId().toString(),
                seatReleasedTopic,
                event
        );
        log.info("[ReservationSeatReleasedEvent] Outbox 등록 — reservationSeatId={}, matchId={}, seatGradeId={}, reason={}",
                event.reservationSeatId(), event.matchId(), event.seatGradeId(), event.reason());
    }
}

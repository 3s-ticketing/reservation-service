package org.ticketing.reservation.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.ticketing.common.event.Events;
import org.ticketing.reservation.domain.event.ReservationEventPublisher;
import org.ticketing.reservation.domain.event.payload.ReservationCancelledEvent;
import org.ticketing.reservation.domain.event.payload.ReservationCompletedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationConfirmationFailedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatHeldEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.reservation.domain.event.payload.ReservationSeatReservedEvent;

/**
 * Reservation 도메인 이벤트 발행 구현체.
 *
 * <p>예매(루트) 및 좌석(자식) 라이프사이클 이벤트를 모두 발행한다.
 * 두 어그리게이트가 동일한 인프라(Outbox + Spring ApplicationEvent)를 공유하므로
 * 단일 구현체에서 양쪽 인터페이스를 모두 구현한다.
 *
 * <h3>발행 채널</h3>
 * <ul>
 *   <li><b>Kafka (Outbox)</b> — 다른 서비스가 소비하는 이벤트.
 *       {@link Events#trigger} 호출 시 OutboxEventListener 가 같은 트랜잭션에 레코드를 삽입하고,
 *       AFTER_COMMIT 이후 릴레이 스케줄러가 Kafka 로 전송한다.</li>
 *   <li><b>Spring ApplicationEvent</b> — 같은 JVM 내 리스너가 소비하는 이벤트.
 *       ticket 모듈이 별도 서비스로 분리되면 Outbox 방식으로 교체한다.</li>
 * </ul>
 *
 * <h3>채널 매핑</h3>
 * <ul>
 *   <li>{@link #publishCompleted} — Spring ApplicationEvent. 티켓 발급 리스너 트리거.</li>
 *   <li>{@link #publishCancelled} — Kafka. payment-service 환불 트리거.</li>
 *   <li>{@link #publishConfirmationFailed} — Kafka. payment-service DLT 보상 트리거.</li>
 *   <li>{@link #publishReserved} — Kafka. match-service 잔여 좌석 수 감소.</li>
 *   <li>{@link #publishReleased} — Kafka. match-service 잔여 좌석 수 증가.</li>
 *   <li>{@link #publishHeld} — 외부 연동 없음. 로그만 기록.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventPublisherImpl
        implements ReservationEventPublisher {

    private static final String DOMAIN_TYPE_RESERVATION = "RESERVATION";
    private static final String DOMAIN_TYPE_SEAT = "RESERVATION_SEAT";

    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${topics.reservation.canceled:reservation.canceled}")
    private String canceledTopic;

    @Value("${topics.reservation.confirmation-failed:reservation.confirmation.failed}")
    private String confirmationFailedTopic;

    @Value("${topics.reservation.seat.reserved:reservation.seat.reserved}")
    private String seatReservedTopic;

    @Value("${topics.reservation.seat.released:reservation.seat.released}")
    private String seatReleasedTopic;

    // ─── Reservation 어그리게이트 이벤트 ──────────────────────────────────────

    @Override
    public void publishCompleted(ReservationCompletedEvent event) {
        applicationEventPublisher.publishEvent(event);
        log.info("[ReservationCompletedEvent] Spring 이벤트 발행 - reservationId: {}",
                event.reservationId());
    }

    @Override
    public void publishCancelled(ReservationCancelledEvent event) {
        Events.trigger(
                "reservation-cancelled-" + event.reservationId(),   // correlationId (멱등성 보장)
                DOMAIN_TYPE_RESERVATION,
                event.reservationId().toString(),                    // domainId (Kafka 파티션 키)
                canceledTopic,                                       // eventType = topic
                event                                                // payload
        );
        log.info("[ReservationCancelledEvent] Outbox 등록 - reservationId: {}, reason: {}",
                event.reservationId(), event.cancelReason());
    }

    @Override
    public void publishConfirmationFailed(ReservationConfirmationFailedEvent event) {
        Events.trigger(
                "reservation-confirmation-failed-" + event.reservationId(),
                DOMAIN_TYPE_RESERVATION,
                event.reservationId().toString(),
                confirmationFailedTopic,
                event
        );
        log.info("[ReservationConfirmationFailedEvent] Outbox 등록 - reservationId: {}",
                event.reservationId());
    }

    // ─── ReservationSeat 어그리게이트 이벤트 ──────────────────────────────────

    @Override
    public void publishHeld(ReservationSeatHeldEvent event) {
        // HOLD 이벤트는 외부 서비스 연동 불필요 — 로그만 기록
        log.debug("[이벤트] 좌석 HOLD — seatId={}, matchId={}",
                event.seatId(), event.matchId());
    }

    @Override
    public void publishReserved(ReservationSeatReservedEvent event) {
        Events.trigger(
                "reservation-seat-reserved-" + event.reservationSeatId(),
                DOMAIN_TYPE_SEAT,
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
                DOMAIN_TYPE_SEAT,
                event.matchId().toString(),
                seatReleasedTopic,
                event
        );
        log.info("[ReservationSeatReleasedEvent] Outbox 등록 — reservationSeatId={}, matchId={}, seatGradeId={}, reason={}",
                event.reservationSeatId(), event.matchId(), event.seatGradeId(), event.reason());
    }
}
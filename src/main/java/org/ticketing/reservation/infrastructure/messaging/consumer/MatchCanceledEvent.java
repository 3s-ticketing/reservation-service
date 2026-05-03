package org.ticketing.reservation.infrastructure.messaging.consumer;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * match-service 가 발행하는 match.canceled 이벤트 페이로드.
 *
 * <p>경기가 취소될 때 발행되며, reservation-service 는 이를 소비하여
 * 해당 경기의 모든 진행 중인 예매를 일괄 취소한다.
 */
public record MatchCanceledEvent(
        UUID matchId,
        OffsetDateTime canceledAt
) {
}

package org.ticketing.reservation.domain.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.ticketing.reservation.domain.model.redis.SeatHold;

/**
 * 좌석 락(HOLD / RESERVED) Redis 진입점.
 *
 * <p>같은 키를 두 상태가 공유하므로 {@code hold()} 는 어떤 상태든 이미 점유 중이면 실패한다.
 * 이렇게 단일 SETNX 한 번으로 HOLD·RESERVED 양쪽 모두를 차단할 수 있다.
 */
public interface SeatHoldRepository {

    /**
     * 좌석 선점 시도. 키가 없을 때만 HOLD 페이로드로 생성. TTL 600초.
     *
     * @return 성공 시 true. 이미 다른 사용자가 HOLD/RESERVED 중이면 false.
     */
    boolean hold(UUID matchId, UUID seatId, SeatHold value);

    /**
     * HOLD → RESERVED 전이. 결제 확정 시 호출.
     *
     * <p>원자성:
     * <ol>
     *   <li>현재 키가 존재하고</li>
     *   <li>state == HOLD 이며</li>
     *   <li>reservationId 가 일치할 때만</li>
     *   <li>RESERVED 페이로드 + 새 TTL 로 덮어쓴다.</li>
     * </ol>
     * 위 4단계는 Lua 스크립트로 단일 원자 연산으로 처리된다.
     *
     * @return 전이 성공 시 true. HOLD 만료/소실, owner 불일치, 이미 RESERVED 였음 등은 false.
     */
    boolean confirm(UUID matchId, UUID seatId, SeatHold reservedValue, Duration ttl);

    /** 현재 락 정보 조회 (HOLD 든 RESERVED 든). */
    Optional<SeatHold> find(UUID matchId, UUID seatId);

    /** 락 해제. cancel/expire 흐름에서 사용. */
    void release(UUID matchId, UUID seatId);

    /** 특정 reservation 의 활성 HOLD 개수 (예매당 최대 좌석 수 제한 등에 사용). */
    int countHoldsByReservationId(UUID reservationId);
}

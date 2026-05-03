package org.ticketing.reservation.application.service;

import java.util.List;
import java.util.UUID;

/**
 * cancel / expire 후 Redis 락을 정리해야 할 좌석 목록 캡처.
 *
 * <p>{@link ReservationWriteService} 의 쓰기 트랜잭션 안에서 수집된 뒤
 * {@link ReservationApplicationService} 로 반환된다.
 * 트랜잭션 커밋 후 Redis release/confirm 대상을 결정하는 데 사용된다.
 *
 * @param matchId  경기 ID (Redis 키 구성에 사용)
 * @param seatIds  정리 대상 좌석 ID 목록
 */
record SeatCleanupTarget(UUID matchId, List<UUID> seatIds) {}

package org.ticketing.reservation.application.service;

import java.util.List;
import java.util.UUID;

/**
 * cancel / expire / confirm 후 Redis 락을 정리해야 할 좌석 목록 캡처.
 *
 * <p>{@link ReservationWriteService} 의 쓰기 트랜잭션 또는 {@link ReservationApplicationService}
 * 의 read tx 안에서 수집된 뒤, 트랜잭션 커밋 후 Redis release/confirm 대상을 결정하는 데 사용된다.
 *
 * <p>{@code reservationId} 와 {@code userId} 는 confirm 경로에서 Redis RESERVED 페이로드를
 * 재구성할 때 필요하다 (HOLD 키가 자연 만료된 뒤 결제완료가 늦게 도착한 경우, 키를 새로
 * SET 해 다른 사용자의 점유를 차단하기 위함).
 *
 * @param matchId       경기 ID (Redis 키 구성)
 * @param reservationId 예매 ID (RESERVED 페이로드 owner)
 * @param userId        사용자 ID (RESERVED 페이로드 owner)
 * @param seatIds       정리 대상 좌석 ID 목록
 */
record SeatCleanupTarget(UUID matchId, UUID reservationId, UUID userId, List<UUID> seatIds) {}

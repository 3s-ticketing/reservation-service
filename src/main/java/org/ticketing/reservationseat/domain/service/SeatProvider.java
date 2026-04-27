package org.ticketing.reservationseat.domain.service;

import java.util.UUID;

/**
 * 외부 stadium/seat 도메인 데이터 접근 추상.
 *
 * <p>좌석 ID 의 유효성, 좌석 번호·등급 등 메타데이터를 가져올 때 사용한다.
 * 도메인 계층은 본 인터페이스만 의존하며, 실제 구현은
 * infrastructure.provider 에 위치하여 FeignClient 호출을 위임한다.
 */
public interface SeatProvider {

    /** 좌석이 존재하고 본 경기에 사용 가능한지 검증. */
    boolean existsAndUsable(UUID matchId, UUID seatId);

    /**
     * 외부 좌석 메타 정보 스냅샷.
     */
    SeatSnapshot fetchSnapshot(UUID matchId, UUID seatId);

    record SeatSnapshot(
            UUID seatId,
            UUID stadiumId,
            UUID seatGradeId,
            String seatNumber,
            Long price
    ) {
    }
}

package org.ticketing.reservation.infrastructure.provider;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.ticketing.reservation.domain.service.SeatProvider;

/**
 * {@link SeatProvider} 의 임시 구현.
 *
 * <p>실제 stadium/match 서비스의 좌석 메타데이터를 가져오는
 * FeignClient 기반 구현은 후속 브랜치(외부 통신 브랜치)에서 교체될 예정이다.
 * 현재는 컨텍스트 부트업 + CRUD 골격을 검증할 수 있도록 합리적인 기본값을 반환한다.
 *
 * <p>본 빈은 좌석 점유의 도메인 가드만 통과시키며, 실제 가격/등급/번호는
 * 후속 브랜치에서 외부 호출 결과로 대체된다.
 */
@Component
public class SeatProviderImpl implements SeatProvider {

    /** 임시 가격(추후 stadium-service 의 seat_grade.price 로 대체). */
    private static final Long DEFAULT_SEAT_PRICE = 0L;

    /** 임시 stadium ID(추후 match → stadium 조회 결과로 대체). */
    private static final UUID DEFAULT_STADIUM_ID = new UUID(0L, 0L);

    /** 임시 seat_grade ID(추후 stadium-service 응답으로 대체). */
    private static final UUID DEFAULT_SEAT_GRADE_ID = new UUID(0L, 0L);

    @Override
    public boolean existsAndUsable(UUID matchId, UUID seatId) {
        // TODO: stadium-service Feign 호출로 대체.
        return matchId != null && seatId != null;
    }

    @Override
    public SeatSnapshot fetchSnapshot(UUID matchId, UUID seatId) {
        // TODO: stadium-service Feign 호출 결과로 대체.
        return new SeatSnapshot(
                seatId,
                DEFAULT_STADIUM_ID,
                DEFAULT_SEAT_GRADE_ID,
                "TBD",
                DEFAULT_SEAT_PRICE
        );
    }
}

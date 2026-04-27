package org.ticketing.reservationseat.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ticketing.reservationseat.domain.model.ReservationSeat;

public interface ReservationSeatRepository {

    ReservationSeat save(ReservationSeat seat);

    /**
     * 즉시 플러시한다.
     * 도메인 가드 통과 후 DB 유니크 제약(uq_reservation_seat_active) 위반을
     * 트랜잭션 내에서 즉시 감지해야 할 때 사용한다.
     */
    ReservationSeat saveAndFlush(ReservationSeat seat);

    Optional<ReservationSeat> findById(UUID id);

    Optional<ReservationSeat> findActiveById(UUID id);

    List<ReservationSeat> findAllByReservationId(UUID reservationId);

    /**
     * HOLD 상태이면서 만료 시각이 지난 점유 레코드를 일괄 조회.
     * 만료 스케줄러에서 사용한다.
     */
    List<ReservationSeat> findHeldOlderThan(LocalDateTime threshold);
}

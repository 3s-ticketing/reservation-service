package org.ticketing.reservation.infrastructure.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketing.reservationseat.domain.model.entity.ReservationSeat;

public interface ReservationSeatJpaRepository extends JpaRepository<ReservationSeat, UUID> {

    // soft delete 미적용 레코드만 조회
    @Query("""
            SELECT rs FROM ReservationSeat rs
            WHERE rs.id = :id AND rs.deletedAt IS NULL
            """)
    Optional<ReservationSeat> findActiveById(@Param("id") UUID id);

    @Query("""
            SELECT rs FROM ReservationSeat rs
            WHERE rs.reservationId = :reservationId AND rs.deletedAt IS NULL
            """)
    List<ReservationSeat> findAllByReservationId(@Param("reservationId") UUID reservationId);

    // 만료 스케줄러용 - HOLD 상태이면서 threshold 이전에 생성된 레코드
    @Query("""
            SELECT rs FROM ReservationSeat rs
            WHERE rs.seatStatus = 'HOLD'
            AND rs.createdAt < :threshold
            AND rs.deletedAt IS NULL
            """)
    List<ReservationSeat> findHeldOlderThan(@Param("threshold") LocalDateTime threshold);

    // 비관적 락 - isActive() = HOLD or RESERVED
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT rs FROM ReservationSeat rs
        WHERE rs.seatId = :seatId
        AND rs.matchId = :matchId
        AND rs.seatStatus IN ('HOLD', 'RESERVED')
        AND rs.deletedAt IS NULL
        """)
    Optional<ReservationSeat> findHoldOrReservedSeat(
            @Param("seatId") UUID seatId,
            @Param("matchId") UUID matchId
    );

    // 4매 제한 - isActive() 기준으로 카운트
    @Query("""
        SELECT COUNT(rs) FROM ReservationSeat rs
        WHERE rs.reservationId = :reservationId
        AND rs.seatStatus IN ('HOLD', 'RESERVED')
        AND rs.deletedAt IS NULL
        """)
    int countActiveByReservationId(@Param("reservationId") UUID reservationId);

}
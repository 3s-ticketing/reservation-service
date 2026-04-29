package org.ticketing.reservation.infrastructure.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketing.reservation.domain.model.ReservationSeat;
import org.ticketing.reservation.domain.model.ReservationSeatStatus;

public interface ReservationSeatJpaRepository extends JpaRepository<ReservationSeat, UUID> {

    @Query("""
            SELECT rs FROM ReservationSeat rs
            WHERE rs.id = :id AND rs.deletedAt IS NULL
            """)
    Optional<ReservationSeat> findActiveById(@Param("id") UUID id);

    // 팀원 엔티티가 @ManyToOne Reservation reservation 이면 reservation.id로 조회
    @Query("""
            SELECT rs FROM ReservationSeat rs
            WHERE rs.reservation.id = :reservationId AND rs.deletedAt IS NULL
            """)
    List<ReservationSeat> findAllByReservationId(@Param("reservationId") UUID reservationId);

    // RESERVED 중복 방지 - 최후 방어선
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rs FROM ReservationSeat rs
            WHERE rs.matchId = :matchId
            AND rs.seatId = :seatId
            AND rs.seatStatus = 'RESERVED'
            AND rs.deletedAt IS NULL
            """)
    Optional<ReservationSeat> findActiveByMatchIdAndSeatId(
            @Param("matchId") UUID matchId,
            @Param("seatId") UUID seatId
    );

    // 경기 취소 일괄 처리
    @Query("""
            SELECT rs FROM ReservationSeat rs
            WHERE rs.matchId = :matchId
            AND rs.seatStatus = :status
            AND rs.deletedAt IS NULL
            """)
    List<ReservationSeat> findAllByMatchIdAndStatus(
            @Param("matchId") UUID matchId,
            @Param("status") ReservationSeatStatus status
    );
}
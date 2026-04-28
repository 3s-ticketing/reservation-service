package org.ticketing.reservation.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.model.ReservationStatus;

public interface JpaReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query("select r from Reservation r where r.id = :id and r.deletedAt is null")
    Optional<Reservation> findActiveById(@Param("id") UUID id);

    @Query("select r from Reservation r where r.userId = :userId and r.deletedAt is null order by r.createdAt desc")
    List<Reservation> findAllByUserId(@Param("userId") UUID userId);

    @Query("select r from Reservation r where r.matchId = :matchId and r.status = :status and r.deletedAt is null")
    List<Reservation> findAllByMatchIdAndStatus(@Param("matchId") UUID matchId,
                                                @Param("status") ReservationStatus status);
}

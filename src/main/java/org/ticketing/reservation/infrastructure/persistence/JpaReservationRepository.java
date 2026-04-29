package org.ticketing.reservation.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketing.reservation.domain.model.Reservation;
import org.ticketing.reservation.domain.model.ReservationStatus;

/**
 * Spring Data JPA 어댑터.
 *
 * <p>어그리게이트 루트(Reservation) + 자식 좌석을 함께 다루기 위해
 * 단건 조회는 {@link EntityGraph} 로 좌석을 한 번에 끌어온다(N+1 방지).
 * 목록 조회는 좌석 표현이 필요한 응답에서 추후 join fetch 로 보강할 수 있다.
 */
public interface JpaReservationRepository extends JpaRepository<Reservation, UUID> {

    @EntityGraph(attributePaths = "seats")
    @Query("select r from Reservation r where r.id = :id and r.deletedAt is null")
    Optional<Reservation> findActiveById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "seats")
    @Query("select distinct r from Reservation r where r.userId = :userId and r.deletedAt is null order by r.createdAt desc")
    List<Reservation> findAllByUserId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = "seats")
    @Query("select distinct r from Reservation r where r.matchId = :matchId and r.status = :status and r.deletedAt is null")
    List<Reservation> findAllByMatchIdAndStatus(@Param("matchId") UUID matchId,
                                                @Param("status") ReservationStatus status);
}

package org.ticketing.ticket.infrastructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketing.ticket.domain.model.entity.Ticket;

public interface TicketJpaRepository extends JpaRepository<Ticket, UUID> {

    @Query("SELECT t FROM Ticket t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Ticket> findActiveById(@Param("id") UUID id);

    @Query("SELECT t FROM Ticket t WHERE t.reservationId = :reservationId AND t.deletedAt IS NULL")
    Optional<Ticket> findActiveByReservationId(@Param("reservationId") UUID reservationId);

    @Query("SELECT t FROM Ticket t WHERE t.userId = :userId AND t.deletedAt IS NULL")
    List<Ticket> findAllByUserId(@Param("userId") UUID userId);
}
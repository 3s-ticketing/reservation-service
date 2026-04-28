package org.ticketing.ticket.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.ticketing.ticket.domain.model.entity.Ticket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {
    Ticket save(Ticket ticket);

    Optional<Ticket> findById(UUID id);

    Optional<Ticket> findActiveById(@Param("id") UUID id);

    Optional<Ticket> findActiveByReservationId(UUID reservationId);

    List<Ticket> findAllByUserId(UUID userId);
}

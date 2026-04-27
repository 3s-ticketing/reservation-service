package org.ticketing.ticket.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ticketing.ticket.domain.model.Ticket;

public interface TicketRepository {

    Ticket save(Ticket ticket);

    Optional<Ticket> findById(UUID id);

    Optional<Ticket> findActiveById(UUID id);

    Optional<Ticket> findActiveByReservationId(UUID reservationId);

    List<Ticket> findAllByUserId(UUID userId);
}

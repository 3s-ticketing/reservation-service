package org.ticketing.ticket.infrastructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.ticketing.ticket.domain.model.entity.Ticket;
import org.ticketing.ticket.domain.repository.TicketRepository;

@Repository
@RequiredArgsConstructor
public class TicketRepositoryImpl implements TicketRepository {

    private final TicketJpaRepository jpaRepository;

    @Override
    public Ticket save(Ticket ticket) {
        return jpaRepository.save(ticket);
    }

    @Override
    public Optional<Ticket> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Ticket> findActiveById(UUID id) {
        return jpaRepository.findActiveById(id);
    }

    @Override
    public Optional<Ticket> findActiveByReservationId(UUID reservationId) {
        return jpaRepository.findActiveByReservationId(reservationId);
    }

    @Override
    public List<Ticket> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId);
    }
}
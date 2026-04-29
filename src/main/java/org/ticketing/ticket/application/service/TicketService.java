package org.ticketing.ticket.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.common.exception.ConflictException;
import org.ticketing.ticket.application.dto.command.CancelTicketCommand;
import org.ticketing.ticket.application.dto.command.IssueTicketCommand;
import org.ticketing.ticket.application.dto.command.UseTicketCommand;
import org.ticketing.ticket.application.dto.result.TicketResult;
import org.ticketing.ticket.domain.event.TicketEventPublisher;
import org.ticketing.ticket.domain.event.payload.TicketCanceledEvent;
import org.ticketing.ticket.domain.event.payload.TicketIssuedEvent;
import org.ticketing.ticket.domain.event.payload.TicketUsedEvent;
import org.ticketing.ticket.domain.exception.TicketNotFoundException;
import org.ticketing.ticket.domain.model.entity.Ticket;
import org.ticketing.ticket.domain.repository.TicketRepository;
import org.ticketing.ticket.domain.service.QrProvider;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final QrProvider qrProvider;
    private final TicketEventPublisher eventPublisher;

    @Transactional
    public TicketResult issue(IssueTicketCommand command) {
        // 중복 발급 방지
        ticketRepository.findActiveByReservationId(command.reservationId())
                .ifPresent(t -> {
                    throw new ConflictException(
                            "이미 발급된 티켓이 존재합니다. reservationId=" + command.reservationId());
                });

        String qr = qrProvider.issueQr(command.reservationId(), command.userId());

        Ticket ticket = Ticket.issue(command.userId(), command.reservationId(), qr);
        Ticket saved = ticketRepository.save(ticket);

        eventPublisher.publishIssued(new TicketIssuedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getReservationId(),
                saved.getQr(),
                OffsetDateTime.now()
        ));

        return TicketResult.from(saved);
    }

    @Transactional
    public TicketResult use(UseTicketCommand command) {
        Ticket ticket = ticketRepository.findActiveById(command.ticketId())
                .orElseThrow(() -> new TicketNotFoundException(command.ticketId()));

        ticket.use();

        eventPublisher.publishUsed(new TicketUsedEvent(
                ticket.getId(),
                ticket.getReservationId(),
                ticket.getUserId(),
                OffsetDateTime.now()
        ));

        return TicketResult.from(ticket);
    }

    @Transactional
    public TicketResult cancel(CancelTicketCommand command) {
        Ticket ticket = ticketRepository.findActiveById(command.ticketId())
                .orElseThrow(() -> new TicketNotFoundException(command.ticketId()));

        ticket.cancel();

        eventPublisher.publishCanceled(new TicketCanceledEvent(
                ticket.getId(),
                ticket.getReservationId(),
                ticket.getUserId(),
                OffsetDateTime.now()
        ));

        return TicketResult.from(ticket);
    }

    @Transactional
    public void delete(UUID ticketId, String deletedBy) {
        Ticket ticket = ticketRepository.findActiveById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        ticket.delete(deletedBy);
    }

    // 쿼리

    public TicketResult getTicketByReservation(UUID reservationId) {
        return ticketRepository.findActiveByReservationId(reservationId)
                .map(TicketResult::from)
                .orElseThrow(() -> TicketNotFoundException.byReservation(reservationId));
    }

    public List<TicketResult> getMyTickets(UUID userId) {
        return ticketRepository.findAllByUserId(userId)
                .stream()
                .map(TicketResult::from)
                .toList();
    }
}
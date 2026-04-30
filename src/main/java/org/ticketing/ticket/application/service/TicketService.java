package org.ticketing.ticket.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.common.exception.ConflictException;
import org.ticketing.common.exception.ForbiddenException;
import org.ticketing.common.exception.InternalServerException;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final QrProvider qrProvider;
    private final TicketEventPublisher eventPublisher;

    @Transactional
    public TicketResult issue(IssueTicketCommand command) {
        try {
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

            return TicketResult.from(saved); // ✅ return 하나만 유지
        } catch (DataIntegrityViolationException e) {
            log.warn("[티켓] 중복 발급 시도 - reservationId={}", command.reservationId());
            return ticketRepository.findActiveByReservationId(command.reservationId())
                    .map(TicketResult::from)
                    .orElseThrow(() -> new InternalServerException("티켓 발급 처리 중 오류가 발생했습니다."));
        }
    }

    @Transactional
    public TicketResult use(UseTicketCommand command) {
        Ticket ticket = ticketRepository.findActiveById(command.ticketId())
                .orElseThrow(() -> new TicketNotFoundException(command.ticketId()));

        // ✅ 소유자이거나 ADMIN/CLUB_ADMIN인 경우만 허용
        if (!ticket.getUserId().equals(command.userId()) && !command.isAdmin()) {
            throw new ForbiddenException("티켓 사용 권한이 없습니다.");
        }

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
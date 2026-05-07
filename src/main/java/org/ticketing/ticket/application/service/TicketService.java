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

        if (!ticket.getUserId().equals(command.userId()) && !command.isAdmin()) {
            throw new ForbiddenException("티켓 취소 권한이 없습니다.");
        }

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

    /**
     * 환불 등 시스템 이벤트로 인한 티켓 취소 + 소프트 삭제.
     *
     * <p>reservationId 로 활성 티켓을 조회한 뒤, 단일 트랜잭션 안에서
     * 상태를 CANCELED 로 전이하고 소프트 삭제까지 완료한다.
     *
     * <h3>멱등성</h3>
     * <ul>
     *   <li>티켓이 없는 경우(미발급 또는 이미 삭제됨) — 무시</li>
     *   <li>이미 CANCELED 상태인 경우 — 상태 전이 없이 삭제만 진행</li>
     * </ul>
     *
     * @param reservationId 예매 ID
     * @param deletedBy     삭제 주체 식별자 (예: "payment.refunded")
     */
    @Transactional
    public void cancelAndDeleteByReservationId(UUID reservationId, String deletedBy) {
        Ticket ticket = ticketRepository.findActiveByReservationId(reservationId)
                .orElse(null);

        if (ticket == null) {
            log.info("[Ticket] 취소·삭제 대상 티켓 없음 (skip) — reservationId={}", reservationId);
            return;
        }

        // AVAILABLE → CANCELED 전이 + 이벤트 발행
        if (ticket.getStatus() == org.ticketing.ticket.domain.model.enums.TicketStatus.AVAILABLE) {
            ticket.cancel();
            eventPublisher.publishCanceled(new TicketCanceledEvent(
                    ticket.getId(),
                    ticket.getReservationId(),
                    ticket.getUserId(),
                    java.time.OffsetDateTime.now()
            ));
        }
        // 이미 CANCELED 상태이면 상태 전이 없이 삭제만 진행

        ticket.delete(deletedBy);
        log.info("[Ticket] 환불 처리 — 티켓 취소+삭제 완료. ticketId={}, reservationId={}",
                ticket.getId(), reservationId);
    }

    // 쿼리

    public TicketResult getTicketByReservation(UUID reservationId, UUID requesterId, boolean isAdmin) {
        Ticket ticket = ticketRepository.findActiveByReservationId(reservationId)
                .orElseThrow(() -> TicketNotFoundException.byReservation(reservationId));

        if (!ticket.getUserId().equals(requesterId) && !isAdmin) {
            throw new ForbiddenException("본인의 티켓만 조회할 수 있습니다.");
        }

        return TicketResult.from(ticket);
    }
}
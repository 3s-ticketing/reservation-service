package org.ticketing.ticket.presentation;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.ticket.application.dto.command.CancelTicketCommand;
import org.ticketing.ticket.application.dto.command.IssueTicketCommand;
import org.ticketing.ticket.application.dto.command.UseTicketCommand;
import org.ticketing.ticket.application.dto.result.TicketResult;
import org.ticketing.ticket.application.service.TicketService;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    // ───────────────── Command ─────────────────

    // 티켓 발급 (예매 완료 후 호출 : 추후 Kafka Consumer로 대체)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResult issue(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam UUID reservationId
    ) {
        return ticketService.issue(new IssueTicketCommand(userId, reservationId));
    }

    // 입장 처리 (QR 스캔 후 USED 상태로 전이)
    // ADMIN, CLUB_ADMIN 권한 (추후 게이트웨이에서 처리)
    @PatchMapping("/{ticketId}/use")
    public TicketResult use(
            @PathVariable UUID ticketId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ticketService.use(new UseTicketCommand(ticketId, userId));
    }

    // 티켓 취소 (예매 취소 시 - 추후 Kafka Consumer로 대체)
    @PatchMapping("/{ticketId}/cancel")
    public TicketResult cancel(
            @PathVariable UUID ticketId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ticketService.cancel(new CancelTicketCommand(ticketId, userId));
    }

    // 티켓 삭제
    @DeleteMapping("/{ticketId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID ticketId,
            @RequestHeader("X-User-Id") String deletedBy
    ) {
        ticketService.delete(ticketId, deletedBy);
    }

    // 쿼리
    // 예매 티켓 상세 조회
    @GetMapping("/reservation/{reservationId}")
    public TicketResult getTicketByReservation(@PathVariable UUID reservationId) {
        return ticketService.getTicketByReservation(reservationId);
    }

    // 내 티켓 목록 조회
    @GetMapping("/me")
    public List<TicketResult> getMyTickets(@RequestHeader("X-User-Id") UUID userId) {
        return ticketService.getMyTickets(userId);
    }

    @PatchMapping("/verify")
    public TicketResult verify(
            @RequestParam UUID reservationId,
            @RequestHeader("X-User-Id") UUID adminId
    ) {
        TicketResult ticket = ticketService.getTicketByReservation(reservationId);
        return ticketService.use(new UseTicketCommand(ticket.id(), adminId));
    }
}
package org.ticketing.ticket.presentation;

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
import org.ticketing.common.exception.ForbiddenException;

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
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role // ✅ 추가
    ) {
        boolean isAdmin = role.equals("ADMIN") || role.equals("CLUB_ADMIN");
        return ticketService.use(new UseTicketCommand(ticketId, userId, isAdmin));
    }

    // 티켓 취소 (예매 취소 시 - 추후 Kafka Consumer로 대체)
    @PatchMapping("/{ticketId}/cancel")
    public TicketResult cancel(
            @PathVariable UUID ticketId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role
    ) {
        boolean isAdmin = role.equals("ADMIN") || role.equals("CLUB_ADMIN");
        return ticketService.cancel(new CancelTicketCommand(ticketId, userId, isAdmin));
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
    public TicketResult getTicketByReservation(
            @PathVariable UUID reservationId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role
    ) {
        boolean isAdmin = role.equals("ADMIN") || role.equals("CLUB_ADMIN");
        return ticketService.getTicketByReservation(reservationId, userId, isAdmin);
    }

    @PatchMapping("/verify")
    public TicketResult verify(
            @RequestParam UUID reservationId,
            @RequestParam UUID userId,
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role
    ) {
        boolean isAdmin = role.equals("ADMIN") || role.equals("CLUB_ADMIN");
        if (!isAdmin) {
            throw new ForbiddenException("입장 처리 권한이 없습니다.");
        }

        TicketResult ticket = ticketService.getTicketByReservation(reservationId, userId, false);
        if (!ticket.userId().equals(userId)) {
            throw new ForbiddenException("QR 코드 소유자가 일치하지 않습니다.");
        }

        return ticketService.use(new UseTicketCommand(ticket.id(), adminId, true));
    }
}
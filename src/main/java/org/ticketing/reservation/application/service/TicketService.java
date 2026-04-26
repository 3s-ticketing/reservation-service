package org.ticketing.reservation.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticketing.reservation.application.dto.ReservationRequest;
import org.ticketing.reservation.application.dto.ReservationResponse;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {
    // 생성
    public ReservationResponse createTicket(ReservationRequest request) {
        return null; // 구현 예정
    }

    // 수정 (상태 변경 등)
    public ReservationResponse updateTicket(UUID id, ReservationRequest request) {
        return null; // 구현 예정
    }

    // 삭제
    public void deleteTicket(UUID id) {
        // 구현 예정
    }

    // 상세 조회
    public ReservationResponse getTicket(UUID id) {
        return null; // 구현 예정
    }

    // 목록 조회
    public List<ReservationResponse> getAllTickets() {
        return List.of(); // 구현 예정
    }
}

package org.ticketing.reservation.application.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticketing.reservation.application.dto.ReservationRequest;
import org.ticketing.reservation.application.dto.ReservationResponse;
import org.ticketing.reservation.domain.model.entity.Reservation;
import org.ticketing.reservation.domain.model.enums.ReservationStatus;
import org.ticketing.reservation.infrastructure.repository.ReservationRepository;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        Reservation reservation = Reservation.builder()
                .userId(request.userId())
                .matchId(request.matchId())
                .status(ReservationStatus.PENDING) // 초기 상태 지정
                .totalPrice(request.totalPrice())
                .build();

        Reservation saved = reservationRepository.save(reservation);
        return toResponse(saved);
    }

    public ReservationResponse getReservation(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 예매가 존재하지 않습니다."));
        return toResponse(reservation);
    }

    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReservationResponse updateReservation(UUID id, ReservationRequest request) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 예매가 존재하지 않습니다."));

        return toResponse(reservation);
    }

    @Transactional
    public void deleteReservation(UUID id) {
        reservationRepository.deleteById(id);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getMatchId(),
                reservation.getStatus(),
                reservation.getTotalPrice()
        );
    }
}

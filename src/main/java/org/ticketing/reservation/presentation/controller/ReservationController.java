package org.ticketing.reservation.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketing.reservation.application.dto.ReservationRequest;
import org.ticketing.reservation.application.dto.ReservationResponse;
import org.ticketing.reservation.application.service.ReservationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@RequestBody ReservationRequest request) {
        return ResponseEntity.ok(reservationService.createReservation(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.getReservation(id));
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getAll() {
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ReservationResponse> update(@PathVariable UUID id, @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(reservationService.updateReservation(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }
}

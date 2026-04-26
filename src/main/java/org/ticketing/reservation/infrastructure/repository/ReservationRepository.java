package org.ticketing.reservation.infrastructure.repository;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketing.reservation.domain.model.entity.Reservation;

import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
}

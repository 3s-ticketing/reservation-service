package org.ticketing.reservation.domain.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;
import org.ticketing.reservation.domain.model.enums.TicketStatus;

import java.util.UUID;

@Entity
@Getter
@Table(name="p_ticket", schema="reservation")
@NoArgsConstructor
public class Ticket extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name="qr", nullable = false, unique = true)
    private String qr;

    @Column(name="user_id", nullable = false)
    private UUID userId;

    @Column(name="reservation_id", nullable = false)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false)
    private TicketStatus status;
}

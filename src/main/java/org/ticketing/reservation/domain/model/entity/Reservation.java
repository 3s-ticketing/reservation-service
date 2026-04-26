package org.ticketing.reservation.domain.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;
import org.ticketing.reservation.domain.model.enums.ReservationStatus;

import java.util.UUID;

@Entity
@Getter
@Table(name="p_reservation", schema="reservation")
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Reservation extends BaseEntity {
    @Id
    @GeneratedValue(strategy= GenerationType.UUID)
    private UUID id;

    @Column(name="user_id", nullable=false)
    private UUID userId;

    @Column(name="match_id", nullable=false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false)
    private ReservationStatus status;

    @Column(name="total_price", nullable=false)
    private Long totalPrice;


}

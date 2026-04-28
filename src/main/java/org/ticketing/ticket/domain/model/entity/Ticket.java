package org.ticketing.ticket.domain.model.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;
import org.ticketing.ticket.domain.exception.InvalidTicketStateException;
import org.ticketing.ticket.domain.model.enums.TicketStatus;

/**
 * 티켓(Ticket) 어그리게이트 루트.
 *
 * <p>예매가 COMPLETED 된 시점에 1:1로 발급된다.
 * QR 문자열은 외부 QR 발급기에서 받은 URL/토큰을 그대로 보존한다.
 */
@Getter
@Entity
@Table(name = "p_ticket")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AttributeOverrides({
        @AttributeOverride(name = "modifiedAt", column = @Column(name = "updated_at", insertable = false)),
        @AttributeOverride(name = "modifiedBy", column = @Column(name = "updated_by", insertable = false))
})
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "qr", nullable = false, length = 300)
    private String qr;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "reservation_id", nullable = false, columnDefinition = "uuid", unique = true)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    @Builder
    private Ticket(String qr, UUID userId, UUID reservationId) {
        this.qr = qr;
        this.userId = userId;
        this.reservationId = reservationId;
        this.status = TicketStatus.AVAILABLE;
    }

    public static Ticket issue(UUID userId, UUID reservationId, String qr) {
        if (qr == null || qr.isBlank()) {
            throw new IllegalArgumentException("QR 값은 필수입니다.");
        }
        return Ticket.builder()
                .qr(qr)
                .userId(userId)
                .reservationId(reservationId)
                .build();
    }

    // ──────────────────────────────────────────
    // 상태 전이
    // ──────────────────────────────────────────

    /** 입장 처리. */
    public void use() {
        transitionTo(TicketStatus.USED);
    }

    /** 예매 취소·환불 등으로 무효화. */
    public void cancel() {
        transitionTo(TicketStatus.CANCELED);
    }

    private void transitionTo(TicketStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidTicketStateException(this.status, target);
        }
        this.status = target;
    }

    public void delete(String deletedBy) {
        super.delete(deletedBy);
    }
}

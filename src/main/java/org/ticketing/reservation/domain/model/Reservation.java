package org.ticketing.reservation.domain.model;

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
import org.ticketing.reservation.domain.exception.InvalidReservationStateException;

/**
 * 예매(Reservation) 어그리게이트 루트.
 *
 * <p>한 사용자가 한 경기에 대해 생성하는 예매 트랜잭션의 골격이다.
 * 좌석 점유는 별도 어그리게이트({@code ReservationSeat})에서 관리하며,
 * Reservation 은 자신을 식별하는 ID 와 합계 금액·상태만 책임진다.
 *
 * <p>{@link BaseEntity} 의 {@code modifiedAt} / {@code modifiedBy} 컬럼은
 * 본 서비스 컨벤션에 맞춰 {@code updated_at} / {@code updated_by} 로 매핑된다.
 */
@Getter
@Entity
@Table(name = "p_reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AttributeOverrides({
        @AttributeOverride(name = "modifiedAt", column = @Column(name = "updated_at", insertable = false)),
        @AttributeOverride(name = "modifiedBy", column = @Column(name = "updated_by", insertable = false))
})
public class Reservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "match_id", nullable = false, columnDefinition = "uuid")
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Builder
    private Reservation(UUID userId, UUID matchId, Long totalPrice) {
        this.userId = userId;
        this.matchId = matchId;
        this.totalPrice = totalPrice;
        this.status = ReservationStatus.PENDING;
    }

    public static Reservation create(UUID userId, UUID matchId, Long totalPrice) {
        if (totalPrice == null || totalPrice < 0L) {
            throw new InvalidReservationStateException("총 금액은 0 이상이어야 합니다.");
        }
        return Reservation.builder()
                .userId(userId)
                .matchId(matchId)
                .totalPrice(totalPrice)
                .build();
    }

    // ──────────────────────────────────────────
    // 상태 전이
    // ──────────────────────────────────────────

    /** 결제 성공 → 예매 확정. */
    public void complete() {
        transitionTo(ReservationStatus.COMPLETED);
    }

    /** 사용자/시스템 취소. */
    public void cancel() {
        transitionTo(ReservationStatus.CANCELLED);
    }

    /** TTL 만료. */
    public void expire() {
        transitionTo(ReservationStatus.EXPIRED);
    }

    private void transitionTo(ReservationStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidReservationStateException(this.status, target);
        }
        this.status = target;
    }

    public void delete(String deletedBy) {
        super.delete(deletedBy);
    }
}

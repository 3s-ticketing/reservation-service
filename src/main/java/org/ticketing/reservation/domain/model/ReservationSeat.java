package org.ticketing.reservation.domain.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;
import org.ticketing.reservation.domain.exception.InvalidReservationSeatStateException;

/**
 * 예약 좌석 엔티티.
 *
 * <p>{@link Reservation} 어그리게이트 루트의 자식 엔티티이다.
 * 외부에서 직접 생성·조회·변경하지 않으며, 항상 루트({@code Reservation})를 통해 다룬다.
 *
 * <p>동일 {@code (match_id, seat_id)} 에 대해 활성(HOLD/RESERVED) 레코드가 동시에
 * 존재하지 않도록 DB 유니크 인덱스 {@code uq_reservation_seat_active} 가
 * 마이그레이션에 포함되어 있다.
 */
@Getter
@Entity
@Table(name = "p_reservation_seat")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AttributeOverrides({
        @AttributeOverride(name = "modifiedAt", column = @Column(name = "updated_at", insertable = false)),
        @AttributeOverride(name = "modifiedBy", column = @Column(name = "updated_by", insertable = false))
})
public class ReservationSeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** 부모 {@link Reservation} 으로의 역참조. 컬럼명은 reservation_id 그대로 유지. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, columnDefinition = "uuid")
    private Reservation reservation;

    @Column(name = "match_id", nullable = false, columnDefinition = "uuid")
    private UUID matchId;

    @Column(name = "stadium_id", nullable = false, columnDefinition = "uuid")
    private UUID stadiumId;

    @Column(name = "seat_id", nullable = false, columnDefinition = "uuid")
    private UUID seatId;

    @Column(name = "seat_grade_id", nullable = false, columnDefinition = "uuid")
    private UUID seatGradeId;

    @Column(name = "seat_number", nullable = false, length = 10)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_status", nullable = false, length = 20)
    private ReservationSeatStatus seatStatus;

    @Column(name = "price", nullable = false)
    private Long price;

    /**
     * 패키지 한정 생성자. 좌석은 {@link Reservation#addSeat} 를 통해서만 생성된다.
     */
    ReservationSeat(Reservation reservation, UUID matchId, UUID stadiumId, UUID seatId,
                    UUID seatGradeId, String seatNumber, Long price) {
        if (price == null || price < 0L) {
            throw new InvalidReservationSeatStateException("좌석 가격은 0 이상이어야 합니다.");
        }
        this.reservation = reservation;
        this.matchId = matchId;
        this.stadiumId = stadiumId;
        this.seatId = seatId;
        this.seatGradeId = seatGradeId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.seatStatus = ReservationSeatStatus.HOLD;
    }

    // ──────────────────────────────────────────
    // 상태 전이 (Reservation 루트에서만 호출됨)
    // ──────────────────────────────────────────

    void confirm() {
        transitionTo(ReservationSeatStatus.RESERVED);
    }

    void expire() {
        transitionTo(ReservationSeatStatus.EXPIRED);
    }

    void cancel() {
        transitionTo(ReservationSeatStatus.CANCELED);
    }

    private void transitionTo(ReservationSeatStatus target) {
        if (!this.seatStatus.canTransitionTo(target)) {
            throw new InvalidReservationSeatStateException(this.seatStatus, target);
        }
        this.seatStatus = target;
    }

    public void delete(String deletedBy) {
        super.delete(deletedBy);
    }
}

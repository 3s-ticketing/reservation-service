package org.ticketing.reservationseat.domain.model;

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
import org.ticketing.reservationseat.domain.exception.InvalidReservationSeatStateException;

/**
 * 예약 좌석(ReservationSeat) 어그리게이트 루트.
 *
 * <p>Reservation 과 분리된 별도 어그리게이트이며, 좌석 점유의 생명주기를 책임진다.
 * 점유 시작 시 {@link ReservationSeatStatus#HOLD} 로 생성되고,
 * 결제가 끝나면 {@code RESERVED} 로 확정된다.
 *
 * <p>동일 (match_id, seat_id) 에 대해 활성(HOLD/RESERVED) 레코드가 동시에
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

    @Column(name = "reservation_id", nullable = false, columnDefinition = "uuid")
    private UUID reservationId;

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

    @Builder
    private ReservationSeat(UUID reservationId, UUID matchId, UUID stadiumId, UUID seatId,
                            UUID seatGradeId, String seatNumber, Long price) {
        this.reservationId = reservationId;
        this.matchId = matchId;
        this.stadiumId = stadiumId;
        this.seatId = seatId;
        this.seatGradeId = seatGradeId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.seatStatus = ReservationSeatStatus.HOLD;
    }

    /**
     * 좌석 점유(HOLD) 생성 팩토리.
     */
    public static ReservationSeat hold(UUID reservationId, UUID matchId, UUID stadiumId,
                                       UUID seatId, UUID seatGradeId,
                                       String seatNumber, Long price) {
        if (price == null || price < 0L) {
            throw new InvalidReservationSeatStateException("좌석 가격은 0 이상이어야 합니다.");
        }
        return ReservationSeat.builder()
                .reservationId(reservationId)
                .matchId(matchId)
                .stadiumId(stadiumId)
                .seatId(seatId)
                .seatGradeId(seatGradeId)
                .seatNumber(seatNumber)
                .price(price)
                .build();
    }

    // ──────────────────────────────────────────
    // 상태 전이
    // ──────────────────────────────────────────

    /** 결제 완료 시 HOLD → RESERVED. */
    public void confirm() {
        transitionTo(ReservationSeatStatus.RESERVED);
    }

    /** TTL 만료 시 HOLD → EXPIRED. */
    public void expire() {
        transitionTo(ReservationSeatStatus.EXPIRED);
    }

    /** 사용자/관리자 취소 시 CANCELED. */
    public void cancel() {
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

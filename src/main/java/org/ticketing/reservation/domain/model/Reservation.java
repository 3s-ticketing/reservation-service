package org.ticketing.reservation.domain.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;
import org.ticketing.reservation.domain.exception.InvalidReservationStateException;
import org.ticketing.reservation.domain.service.SeatProvider.SeatSnapshot;

/**
 * 예매(Reservation) 어그리게이트 루트.
 *
 * <p>한 사용자가 한 경기에 대해 생성하는 예매 트랜잭션의 루트이며,
 * 점유한 좌석 {@link ReservationSeat} 들을 자식 엔티티로 직접 보유한다.
 * 좌석에 대한 모든 변경은 본 루트({@code Reservation})를 통해서만 수행된다.
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

    /**
     * 본 예매에 점유된 좌석 목록.
     *
     * <p>루트와 라이프사이클을 공유한다. 부모가 저장될 때 자식도 함께 저장되며,
     * 컬렉션에서 제거된 좌석은 DB에서도 삭제된다.
     */
    @OneToMany(
            mappedBy = "reservation",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private final List<ReservationSeat> seats = new ArrayList<>();

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
    // 좌석 (자식 엔티티) 관리
    // ──────────────────────────────────────────

    /**
     * 좌석을 HOLD 상태로 본 예매에 추가한다.
     *
     * @param snapshot 외부 좌석 메타 스냅샷 (가격 포함)
     * @return 새로 추가된 자식 좌석 엔티티
     */
    public ReservationSeat addSeat(SeatSnapshot snapshot) {
        if (this.status != ReservationStatus.PENDING) {
            throw new InvalidReservationStateException(
                    "PENDING 상태의 예매만 좌석을 추가할 수 있습니다.");
        }
        ReservationSeat seat = new ReservationSeat(
                this,
                this.matchId,
                snapshot.stadiumId(),
                snapshot.seatId(),
                snapshot.seatGradeId(),
                snapshot.seatNumber(),
                snapshot.price()
        );
        this.seats.add(seat);
        return seat;
    }

    /** 외부에서는 변경 불가능한 좌석 뷰만 제공. */
    public List<ReservationSeat> getSeats() {
        return Collections.unmodifiableList(this.seats);
    }

    /**
     * 특정 좌석 한 건만 사용자 취소.
     *
     * <p>예매 자체는 그대로 두고, 자식 좌석 한 건만 CANCELED 로 전이한다.
     * 모든 좌석이 비활성 상태가 되는 것은 호출자(애플리케이션 서비스)가 판단한다.
     */
    public ReservationSeat releaseSeat(UUID seatId) {
        if (this.status != ReservationStatus.PENDING) {
            throw new InvalidReservationStateException(
                    "PENDING 상태의 예매에서만 개별 좌석을 해제할 수 있습니다.");
        }
        ReservationSeat target = this.seats.stream()
                .filter(seat -> seat.getId().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new InvalidReservationStateException(
                        "예매에 포함되지 않은 좌석입니다. seatId=" + seatId));
        target.cancel();
        return target;
    }

    /** 활성 좌석 가격 합계로 totalPrice 를 재계산한다. */
    public void recalculateTotalPrice() {
        this.totalPrice = this.seats.stream()
                .filter(seat -> seat.getSeatStatus().isActive())
                .mapToLong(ReservationSeat::getPrice)
                .sum();
    }

    // ──────────────────────────────────────────
    // 상태 전이 — 루트가 자식 좌석 상태도 함께 끌고 간다.
    // ──────────────────────────────────────────

    // 예매 확정, 좌석은 addSeat 시점에 이미 RESERVED이므로 상태 전이 불필요
    public void complete() {
        transitionTo(ReservationStatus.COMPLETED);
        // ✅ seats는 이미 RESERVED 상태 - 추가 전이 없음
    }

    // 사용자/시스템 취소 + RESERVED 좌석 CANCELED
    public void cancel() {
        transitionTo(ReservationStatus.CANCELLED);
        this.seats.stream()
                .filter(seat -> seat.getSeatStatus().isActive())
                .forEach(ReservationSeat::cancel);
    }

    // TTL 만료 -> EXPIRED 상태 전이, 좌석은 Redis에만 있었으므로 DB 변경 없음
    public void expire() {
        transitionTo(ReservationStatus.EXPIRED);
        // ✅ HOLD는 Redis-only였으므로 DB seats 변경 없음
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

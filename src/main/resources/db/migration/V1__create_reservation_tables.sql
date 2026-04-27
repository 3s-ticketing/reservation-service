-- ============================================================
-- reservation-service 초기 스키마
-- 세 개의 어그리게이트(reservation / reservation_seat / ticket)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS reservation;

-- ── p_reservation (예매) ──────────────────────────────────────
-- 어그리게이트 루트: Reservation
CREATE TABLE reservation.p_reservation (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         UUID        NOT NULL,
    match_id        UUID        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / COMPLETED / EXPIRED / CANCELLED
    total_price     BIGINT      NOT NULL,
    -- BaseEntity audit
    created_at      TIMESTAMP   NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(255)
);

CREATE INDEX idx_reservation_user_id  ON reservation.p_reservation (user_id)  WHERE deleted_at IS NULL;
CREATE INDEX idx_reservation_match_id ON reservation.p_reservation (match_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_reservation_status   ON reservation.p_reservation (status)   WHERE deleted_at IS NULL;


-- ── p_reservation_seat (예약 좌석) ────────────────────────────
-- 어그리게이트 루트: ReservationSeat
-- 예매(reservation_id)와 별도 어그리게이트로 분리하여 좌석 점유/해제만 책임진다.
CREATE TABLE reservation.p_reservation_seat (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    reservation_id  UUID        NOT NULL,
    match_id        UUID        NOT NULL,
    stadium_id      UUID        NOT NULL,
    seat_id         UUID        NOT NULL,
    seat_grade_id   UUID        NOT NULL,
    seat_number     VARCHAR(10) NOT NULL,
    seat_status     VARCHAR(20) NOT NULL DEFAULT 'HOLD', -- AVAILABLE / HOLD / RESERVED / EXPIRED / CANCELED
    price           BIGINT      NOT NULL,
    -- BaseEntity audit
    created_at      TIMESTAMP   NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(255)
);

-- 동일 경기·좌석에 활성(HOLD/RESERVED) 상태는 단 하나만 존재해야 한다.
CREATE UNIQUE INDEX uq_reservation_seat_active
    ON reservation.p_reservation_seat (match_id, seat_id)
    WHERE deleted_at IS NULL AND seat_status IN ('HOLD', 'RESERVED');

CREATE INDEX idx_reservation_seat_reservation_id
    ON reservation.p_reservation_seat (reservation_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_reservation_seat_match_id
    ON reservation.p_reservation_seat (match_id) WHERE deleted_at IS NULL;


-- ── p_ticket (티켓) ───────────────────────────────────────────
-- 어그리게이트 루트: Ticket
-- 예매가 COMPLETED 된 시점에 1:1로 발급된다.
CREATE TABLE reservation.p_ticket (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    qr              VARCHAR(300) NOT NULL,
    user_id         UUID        NOT NULL,
    reservation_id  UUID        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE / USED / CANCELED
    -- BaseEntity audit
    created_at      TIMESTAMP   NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(255),
    CONSTRAINT uq_ticket_reservation UNIQUE (reservation_id)
);

CREATE INDEX idx_ticket_user_id ON reservation.p_ticket (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ticket_status  ON reservation.p_ticket (status)  WHERE deleted_at IS NULL;

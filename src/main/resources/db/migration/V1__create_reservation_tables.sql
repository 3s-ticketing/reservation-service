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
-- Reservation 어그리게이트의 자식 엔티티. 루트(reservation_id)를 통해서만 접근된다.
--
-- 점유 라이프사이클:
--   HOLD  : Redis(seat:{matchId}:{seatId}, TTL 600s) 만 — DB 미반영
--   RESERVED : 결제 확정 시 DB INSERT + Redis 페이로드 RESERVED 로 전이 (긴 TTL)
--   CANCELED : 사용자/시스템 취소 시 DB UPDATE + Redis DEL
-- 즉 DB 의 seat_status 는 RESERVED 와 CANCELED 두 종착 상태만 가진다.
CREATE TABLE reservation.p_reservation_seat (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    reservation_id  UUID        NOT NULL REFERENCES reservation.p_reservation (id),
    match_id        UUID        NOT NULL,
    stadium_id      UUID        NOT NULL,
    seat_id         UUID        NOT NULL,
    seat_grade_id   UUID        NOT NULL,
    seat_number     VARCHAR(10) NOT NULL,
    seat_status     VARCHAR(20) NOT NULL DEFAULT 'RESERVED', -- RESERVED / CANCELED
    price           BIGINT      NOT NULL,
    -- BaseEntity audit
    created_at      TIMESTAMP   NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(255)
);

-- 동일 경기·좌석에 RESERVED 좌석이 둘 이상 존재할 수 없다.
-- (정합성 백업 — 1차 방어는 Redis 단일 키 SETNX, 본 인덱스는 최후 방어선)
CREATE UNIQUE INDEX uq_reservation_seat_active
    ON reservation.p_reservation_seat (match_id, seat_id)
    WHERE deleted_at IS NULL AND seat_status = 'RESERVED';

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

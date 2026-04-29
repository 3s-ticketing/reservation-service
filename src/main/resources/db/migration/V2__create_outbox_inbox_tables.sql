-- ============================================================
-- common-module 의 Inbox / Outbox 어그리게이트 테이블
-- (Outbox 패턴 + Idempotent Consumer 인프라)
-- ============================================================

-- ── p_outbox (트랜잭션 아웃박스) ──────────────────────────────
-- common-module 의 Outbox extends BaseEntity. @AttributeOverride 가 없어
-- BaseEntity 의 컬럼명(modified_at / modified_by)을 그대로 사용한다.
CREATE TABLE reservation.p_outbox (
    message_id      UUID            NOT NULL PRIMARY KEY,
    correlation_id  VARCHAR(64)     NOT NULL UNIQUE,
    domain_type     VARCHAR(50)     NOT NULL,
    domain_id       VARCHAR(50)     NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING', -- PENDING / PROCESSED / FAILED
    retry_count     INT             NOT NULL DEFAULT 0,
    -- BaseEntity audit (override 없음 → modified_*)
    created_at      TIMESTAMP       NOT NULL,
    created_by      VARCHAR(255)    NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(255),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(255)
);

CREATE INDEX idx_outbox_status ON reservation.p_outbox (status);


-- ── p_inbox (멱등 소비자 / Idempotent Consumer) ───────────────
-- Inbox 는 BaseEntity 를 상속하지 않는다. processed_at 만 보존(@CreatedDate).
CREATE TABLE reservation.p_inbox (
    message_id      UUID            NOT NULL PRIMARY KEY,
    message_group   VARCHAR(50),
    processed_at    TIMESTAMP       NOT NULL
);

CREATE INDEX idx_inbox_message_group ON reservation.p_inbox (message_group);
CREATE INDEX idx_inbox_processed_at  ON reservation.p_inbox (processed_at);

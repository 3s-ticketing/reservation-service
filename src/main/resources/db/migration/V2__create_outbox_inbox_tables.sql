-- ============================================================
-- common-module 의 Outbox / Inbox 인프라 테이블
-- (트랜잭션 아웃박스 패턴 + 멱등 소비자)
--
-- 두 테이블은 common-module 의 엔티티(Outbox / Inbox)에 의해
-- @EntityScan("org.ticketing") 으로 자동 매핑된다.
-- reservation-service 의 Flyway 가 reservation 스키마에 본 테이블을 생성한다.
-- ============================================================

-- ── p_outbox (트랜잭션 아웃박스) ──────────────────────────────
-- common-module 의 Outbox extends BaseEntity. @AttributeOverride 가 없어
-- BaseEntity 의 컬럼명(modified_at / modified_by)을 그대로 사용한다.
--
-- 발행 흐름:
--   1) 도메인 변경 트랜잭션 내에서 Events.trigger(...) 호출
--   2) OutboxEventListener.recordOutbox 가 같은 tx 에서 본 테이블에 INSERT
--   3) 트랜잭션 커밋 후 OutboxEventListener.publish 가 Kafka 로 발행
--   4) 실패 시 OutboxRelayScheduler 가 retry_count <= MAX 인 것 재발행
--   5) MAX 초과 시 {eventType}.DLT 로 격리
--
-- partition_key (nullable):
--   null 이면 publisher 가 domain_id 를 Kafka 파티션 키로 사용한다.
--   다른 도메인 단위로 파티셔닝하고 싶을 때만 명시
--   (예: reservation_seat 이벤트를 matchId 로 순서 보장).
--   common-module Outbox.resolveKafkaKey() 가 fallback 처리.
CREATE TABLE reservation.p_outbox (
    message_id      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    correlation_id  VARCHAR(64)     NOT NULL UNIQUE,
    domain_type     VARCHAR(50)     NOT NULL,
    domain_id       VARCHAR(50)     NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING', -- PENDING / PROCESSED / FAILED
    retry_count     INT             NOT NULL DEFAULT 0,
    partition_key   VARCHAR(100),                                -- nullable, null 이면 domain_id fallback
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
-- Inbox 는 BaseEntity 를 상속하지 않는다. @CreatedDate processed_at 만 보존.
-- @IdempotentConsumer AOP 가 본 테이블의 message_id 존재 여부로 중복 수신을 차단한다.
-- message_group 별로 InboxCleanupScheduler 가 주기적으로 오래된 항목을 정리한다.
CREATE TABLE reservation.p_inbox (
    message_id      UUID            NOT NULL PRIMARY KEY,
    message_group   VARCHAR(50),
    processed_at    TIMESTAMP       NOT NULL
);

CREATE INDEX idx_inbox_message_group ON reservation.p_inbox (message_group);
CREATE INDEX idx_inbox_processed_at  ON reservation.p_inbox (processed_at);

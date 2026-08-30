-- V1__init_schema.sql
-- Customer service initial schema for conversation, message, feedback and quick-question tables.

CREATE TABLE conversations (
    id                  VARCHAR(36)     PRIMARY KEY,
    conversation_no     VARCHAR(64)     NOT NULL UNIQUE,
    customer_id         VARCHAR(64)     NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    started_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at            TIMESTAMP,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE messages (
    id                  VARCHAR(36)     PRIMARY KEY,
    conversation_id     VARCHAR(36)     NOT NULL,
    seq_no              INT             NOT NULL,
    role                VARCHAR(16)     NOT NULL,
    content             TEXT            NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_messages_conversation_seq UNIQUE (conversation_id, seq_no),
    CONSTRAINT chk_seq_no_positive CHECK (seq_no > 0),
    CONSTRAINT chk_message_content_not_empty CHECK (content <> '')
);

CREATE TABLE feedback (
    id                  VARCHAR(36)     PRIMARY KEY,
    conversation_id     VARCHAR(36)     NOT NULL,
    rating              VARCHAR(16)     NOT NULL,
    comment             VARCHAR(500),
    submitted_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feedback_conversation UNIQUE (conversation_id)
);

CREATE TABLE quick_questions (
    id                  VARCHAR(36)     PRIMARY KEY,
    sort_order          INT             NOT NULL,
    question_text       VARCHAR(256)    NOT NULL,
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_quick_question_sort_order CHECK (sort_order >= 0),
    CONSTRAINT chk_quick_question_text_not_empty CHECK (question_text <> '')
);

CREATE TABLE idempotency_records (
    id              VARCHAR(36)     PRIMARY KEY,
    operation       VARCHAR(64)     NOT NULL,
    idempotency_key VARCHAR(128)    NOT NULL,
    request_hash    VARCHAR(64)     NOT NULL,
    response_body   TEXT,
    response_status VARCHAR(16),
    status          VARCHAR(32)     NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_idempotency_key UNIQUE (operation, idempotency_key)
);

INSERT INTO quick_questions (id, sort_order, question_text, enabled) VALUES
    ('0191aa00-0000-7000-8000-000000000001', 1, '我的订单到哪了？', TRUE),
    ('0191aa00-0000-7000-8000-000000000002', 2, '如何申请退款？', TRUE),
    ('0191aa00-0000-7000-8000-000000000003', 3, '如何修改收货地址？', TRUE),
    ('0191aa00-0000-7000-8000-000000000004', 4, '支付方式有哪些？', TRUE),
    ('0191aa00-0000-7000-8000-000000000005', 5, '如何联系人工客服？', TRUE);
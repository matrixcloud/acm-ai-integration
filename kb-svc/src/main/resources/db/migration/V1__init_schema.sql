-- V1__init_schema.sql
-- Knowledge base service initial schema for knowledge bases, documents, document chunks,
-- evaluation suites, evaluation cases, evaluation runs, and evaluation run details.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_bases (
    id                  BIGSERIAL       PRIMARY KEY,
    kb_no               VARCHAR(64)     NOT NULL UNIQUE,
    name                VARCHAR(100)    NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    doc_count           INT             NOT NULL DEFAULT 0,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_kb_name_not_empty CHECK (name <> ''),
    CONSTRAINT chk_kb_doc_count CHECK (doc_count >= 0)
);

CREATE TABLE documents (
    id                  BIGSERIAL       PRIMARY KEY,
    document_no         VARCHAR(64)     NOT NULL UNIQUE,
    kb_id               BIGINT          NOT NULL REFERENCES knowledge_bases(id),
    name                VARCHAR(255)    NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    chunk_count         INT             NOT NULL DEFAULT 0,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_doc_name_not_empty CHECK (name <> ''),
    CONSTRAINT chk_doc_chunk_count CHECK (chunk_count >= 0)
);

CREATE TABLE document_chunks (
    id                  BIGSERIAL       PRIMARY KEY,
    document_id         BIGINT          NOT NULL REFERENCES documents(id),
    seq_no              INT             NOT NULL,
    content             TEXT            NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_document_chunks_doc_seq UNIQUE (document_id, seq_no),
    CONSTRAINT chk_chunk_seq_no_positive CHECK (seq_no > 0),
    CONSTRAINT chk_chunk_content_not_empty CHECK (content <> '')
);

CREATE TABLE evaluation_suites (
    id                  BIGSERIAL       PRIMARY KEY,
    suite_no            VARCHAR(64)     NOT NULL UNIQUE,
    name                VARCHAR(100)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_eval_suite_name_not_empty CHECK (name <> '')
);

CREATE TABLE evaluation_cases (
    id                  BIGSERIAL       PRIMARY KEY,
    suite_id            BIGINT          NOT NULL REFERENCES evaluation_suites(id),
    seq_no              INT             NOT NULL,
    query               TEXT            NOT NULL,
    expected_answer     TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_eval_cases_suite_seq UNIQUE (suite_id, seq_no),
    CONSTRAINT chk_eval_case_seq_no_positive CHECK (seq_no > 0),
    CONSTRAINT chk_eval_case_query_not_empty CHECK (query <> '')
);

CREATE TABLE evaluation_runs (
    id                              BIGSERIAL       PRIMARY KEY,
    run_no                          VARCHAR(64)     NOT NULL UNIQUE,
    kb_no                           VARCHAR(64)     NOT NULL,
    suite_id                        BIGINT          NOT NULL REFERENCES evaluation_suites(id),
    status                          VARCHAR(32)     NOT NULL,
    top_k                           INT             NOT NULL DEFAULT 5,
    context_relevancy_avg           DOUBLE PRECISION NOT NULL DEFAULT 0,
    faithfulness_avg                DOUBLE PRECISION NOT NULL DEFAULT 0,
    answer_relevancy_avg            DOUBLE PRECISION NOT NULL DEFAULT 0,
    context_relevancy_pass_rate     DOUBLE PRECISION NOT NULL DEFAULT 0,
    faithfulness_pass_rate          DOUBLE PRECISION NOT NULL DEFAULT 0,
    answer_relevancy_pass_rate      DOUBLE PRECISION NOT NULL DEFAULT 0,
    started_at                      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at                     TIMESTAMP,
    created_at                      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_eval_run_top_k_positive CHECK (top_k > 0)
);

CREATE TABLE evaluation_run_details (
    id                          BIGSERIAL       PRIMARY KEY,
    run_id                      BIGINT          NOT NULL REFERENCES evaluation_runs(id),
    query                       TEXT            NOT NULL,
    generated_answer            TEXT,
    context_relevancy_score     DOUBLE PRECISION NOT NULL DEFAULT 0,
    faithfulness_score          DOUBLE PRECISION NOT NULL DEFAULT 0,
    answer_relevancy_score      DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_eval_detail_query_not_empty CHECK (query <> '')
);

-- V1__init_schema.sql
-- Order service initial schema for create-order use case.

CREATE TABLE orders (
    id                        BIGSERIAL       PRIMARY KEY,
    order_no                  VARCHAR(64)     NOT NULL UNIQUE,
    customer_id               VARCHAR(64)     NOT NULL,
    status                    VARCHAR(32)     NOT NULL,
    currency                  VARCHAR(3)      NOT NULL,
    item_total                DECIMAL(12, 2)  NOT NULL,
    payable_total             DECIMAL(12, 2)  NOT NULL,
    inventory_reservation_id  VARCHAR(128),
    recipient_name            VARCHAR(64)     NOT NULL,
    recipient_phone           VARCHAR(20)     NOT NULL,
    province                  VARCHAR(64)     NOT NULL,
    city                      VARCHAR(64)     NOT NULL,
    district                  VARCHAR(64)     NOT NULL,
    detail_address           VARCHAR(256)    NOT NULL,
    version                   BIGINT          NOT NULL DEFAULT 0,
    created_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id),
    line_no         INT             NOT NULL,
    sku_id          VARCHAR(64)     NOT NULL,
    product_name    VARCHAR(256)    NOT NULL,
    unit_price      DECIMAL(12, 2) NOT NULL,
    quantity        INT             NOT NULL,
    line_amount     DECIMAL(12, 2) NOT NULL,
    CONSTRAINT uk_order_items_order_line UNIQUE (order_id, line_no),
    CONSTRAINT uk_order_items_order_sku   UNIQUE (order_id, sku_id),
    CONSTRAINT chk_quantity_positive      CHECK (quantity > 0),
    CONSTRAINT chk_amounts_non_negative   CHECK (unit_price >= 0 AND line_amount >= 0)
);

CREATE TABLE idempotency_records (
    id              BIGSERIAL       PRIMARY KEY,
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

CREATE TABLE payments (
    id                  BIGSERIAL       PRIMARY KEY,
    order_id            BIGINT          NOT NULL REFERENCES orders(id),
    payment_no          VARCHAR(64)     NOT NULL UNIQUE,
    external_payment_no VARCHAR(128)    UNIQUE,
    status              VARCHAR(32)     NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    amount              DECIMAL(12, 2)  NOT NULL,
    payment_token       VARCHAR(256)    NOT NULL,
    paid_at             TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_payment_amount_non_negative CHECK (amount >= 0)
);

CREATE TABLE refunds (
    id                  BIGSERIAL       PRIMARY KEY,
    order_id            BIGINT          NOT NULL REFERENCES orders(id),
    refund_no           VARCHAR(64)     NOT NULL UNIQUE,
    type                VARCHAR(32)     NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    reason              VARCHAR(512)    NOT NULL,
    review_comment      VARCHAR(512),
    reviewer            VARCHAR(64),
    external_refund_no  VARCHAR(128)    UNIQUE,
    currency            VARCHAR(3)      NOT NULL,
    amount              DECIMAL(12, 2)  NOT NULL,
    payment_refunded    BOOLEAN         NOT NULL DEFAULT FALSE,
    inventory_restored  BOOLEAN         NOT NULL DEFAULT FALSE,
    reviewed_at         TIMESTAMP,
    refunded_at         TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_refund_amount_non_negative CHECK (amount >= 0)
);

CREATE TABLE shipments (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id),
    shipment_no     VARCHAR(64)     NOT NULL UNIQUE,
    status          VARCHAR(32)     NOT NULL,
    carrier_code    VARCHAR(64)     NOT NULL,
    tracking_no     VARCHAR(128)    NOT NULL UNIQUE,
    shipped_at      TIMESTAMP       NOT NULL,
    delivered_at    TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shipment_items (
    id              BIGSERIAL       PRIMARY KEY,
    shipment_id     BIGINT          NOT NULL REFERENCES shipments(id),
    order_item_id   BIGINT          NOT NULL REFERENCES order_items(id),
    quantity        INT             NOT NULL,
    CONSTRAINT uk_shipment_item UNIQUE (shipment_id, order_item_id),
    CONSTRAINT chk_shipment_quantity_positive CHECK (quantity > 0)
);

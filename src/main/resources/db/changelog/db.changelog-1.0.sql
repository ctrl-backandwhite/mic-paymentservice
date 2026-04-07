-- liquibase formatted sql

-- changeset payment:001-create-payments
CREATE TABLE IF NOT EXISTS payments (
    id              VARCHAR(64)    PRIMARY KEY,
    order_id        VARCHAR(64)    NOT NULL,
    user_id         VARCHAR(64)    NOT NULL,
    amount          NUMERIC(15,2)  NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    payment_method  VARCHAR(20)    NOT NULL,
    provider_ref    VARCHAR(255),
    provider_response JSONB,
    error_message   TEXT,
    idempotency_key VARCHAR(128)   UNIQUE,
    crypto_address  VARCHAR(255),
    crypto_expires_at TIMESTAMPTZ,
    qr_code_url     VARCHAR(512),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id  ON payments(user_id);
CREATE INDEX idx_payments_status   ON payments(status);
CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);

-- changeset payment:002-create-payment-refunds
CREATE TABLE IF NOT EXISTS payment_refunds (
    id              VARCHAR(64)    PRIMARY KEY,
    payment_id      VARCHAR(64)    NOT NULL REFERENCES payments(id),
    amount          NUMERIC(15,2)  NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    reason          TEXT,
    provider_ref    VARCHAR(255),
    created_by      VARCHAR(128),
    updated_by      VARCHAR(128),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_refunds_payment_id ON payment_refunds(payment_id);

CREATE TABLE billing_purchases (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    user_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    encrypted_token BYTEA NOT NULL,
    package_name VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    base_plan_id VARCHAR(255),
    order_id VARCHAR(255),
    official_state VARCHAR(64) NOT NULL,
    subscription BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    acknowledged BOOLEAN NOT NULL,
    starts_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ NOT NULL,
    auto_renew BOOLEAN,
    linked_fingerprint VARCHAR(64),
    superseded BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_billing_purchases_fingerprint UNIQUE (fingerprint),
    CONSTRAINT fk_billing_purchases_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_billing_purchases_user ON billing_purchases (user_id);

CREATE TABLE billing_events (
    id VARCHAR(255) PRIMARY KEY,
    fingerprint VARCHAR(64),
    type VARCHAR(80) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    error_code VARCHAR(80)
);

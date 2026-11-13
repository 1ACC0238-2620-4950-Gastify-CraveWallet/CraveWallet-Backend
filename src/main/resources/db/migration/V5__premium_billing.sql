CREATE TABLE premium_checkouts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES iam_users(id),
    current_owner_id UUID UNIQUE REFERENCES iam_users(id),
    provider_id VARCHAR(128) UNIQUE,
    checkout_url VARCHAR(2048),
    status VARCHAR(32) NOT NULL,
    provider_version BIGINT NOT NULL DEFAULT -1,
    canceled_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_premium_checkout_owner CHECK (current_owner_id IS NULL OR current_owner_id = owner_id)
);

CREATE TABLE premium_payments (
    provider_payment_id VARCHAR(128) PRIMARY KEY,
    provider_invoice_id VARCHAR(128) NOT NULL,
    approved_invoice_id VARCHAR(128) UNIQUE,
    checkout_id UUID NOT NULL REFERENCES premium_checkouts(id),
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_premium_payment_amount CHECK (amount = 9.99 AND currency = 'PEN'),
    CONSTRAINT ck_premium_payment_status CHECK (
        (status = 'approved' AND paid_at IS NOT NULL AND approved_invoice_id IS NOT NULL
            AND approved_invoice_id = provider_invoice_id)
        OR (status = 'rejected' AND paid_at IS NULL AND approved_invoice_id IS NULL)
    )
);
CREATE INDEX ix_premium_payments_checkout ON premium_payments(checkout_id);
CREATE INDEX ix_premium_checkouts_owner ON premium_checkouts(owner_id);

CREATE TABLE premium_webhook_deliveries (
    request_id VARCHAR(128) NOT NULL,
    topic VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (request_id, topic, resource_id)
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES iam_users(id),
    name VARCHAR(100) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency IN ('PEN','USD')),
    category VARCHAR(60) NOT NULL,
    billing_cycle VARCHAR(10) NOT NULL CHECK (billing_cycle IN ('MONTHLY','ANNUAL')),
    next_billing_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE','CANCELLED')),
    cancelled_at TIMESTAMP(6) WITH TIME ZONE,
    CHECK ((status = 'ACTIVE' AND cancelled_at IS NULL) OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL))
);
CREATE INDEX subscriptions_owner_status_date ON subscriptions(owner_id, status, next_billing_date);

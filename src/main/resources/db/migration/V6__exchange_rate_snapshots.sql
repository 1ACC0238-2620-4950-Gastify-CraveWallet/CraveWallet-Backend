CREATE TABLE exchange_rate_snapshots (
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    provider_updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    rate NUMERIC(18,8) NOT NULL CHECK (rate > 0),
    fetched_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (base_currency, quote_currency, provider_updated_at)
);

-- Each version of the billing terms of a subscription, so past cycles keep the amount
-- and schedule that were in force on their billing date.
CREATE TABLE subscription_terms_history (
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    effective_from TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    next_billing_date DATE NOT NULL,
    billing_cycle VARCHAR(10) NOT NULL CHECK (billing_cycle IN ('MONTHLY','ANNUAL')),
    PRIMARY KEY (subscription_id, effective_from)
);

INSERT INTO subscription_terms_history (subscription_id, effective_from, amount, next_billing_date, billing_cycle)
SELECT id, CURRENT_TIMESTAMP, amount, next_billing_date, billing_cycle FROM subscriptions;

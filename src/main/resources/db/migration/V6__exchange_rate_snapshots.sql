CREATE TABLE exchange_rate_snapshots (
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    provider_updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    rate NUMERIC(18,8) NOT NULL CHECK (rate > 0),
    fetched_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (base_currency, quote_currency, provider_updated_at)
);

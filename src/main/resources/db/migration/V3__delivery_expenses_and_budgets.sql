CREATE TABLE delivery_expenses (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES iam_users(id),
    request_id UUID NOT NULL,
    merchant VARCHAR(100) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    category VARCHAR(60) NOT NULL,
    expense_date DATE NOT NULL,
    UNIQUE (owner_id, request_id)
);
CREATE INDEX delivery_expenses_owner_date ON delivery_expenses(owner_id, expense_date);
CREATE TABLE monthly_budgets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES iam_users(id),
    budget_year INTEGER NOT NULL CHECK (budget_year BETWEEN 1900 AND 9999),
    budget_month INTEGER NOT NULL CHECK (budget_month BETWEEN 1 AND 12),
    spending_limit NUMERIC(12,2) CHECK (spending_limit > 0),
    accumulated NUMERIC(19,2) NOT NULL CHECK (accumulated >= 0),
    UNIQUE (owner_id, budget_year, budget_month)
);

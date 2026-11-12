CREATE TABLE premium_memberships (
                                     id UUID PRIMARY KEY,
                                     owner_id UUID NOT NULL UNIQUE REFERENCES iam_users(id),
                                     status VARCHAR(20) NOT NULL,
                                     period_ends_at TIMESTAMP WITH TIME ZONE,
                                     cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,

                                     CONSTRAINT ck_premium_status
                                         CHECK (status IN (
                                                           'PENDING',
                                                           'ACTIVE',
                                                           'PAST_DUE',
                                                           'CANCELED',
                                                           'EXPIRED'
                                             ))
);

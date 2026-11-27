-- Landing page visitors who asked to be notified about the launch (US40).
CREATE TABLE launch_subscribers (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    locale VARCHAR(5) NOT NULL CHECK (locale IN ('en','es')),
    subscribed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

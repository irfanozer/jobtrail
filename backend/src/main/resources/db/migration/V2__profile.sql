CREATE TABLE private_profile (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    lock_version BIGINT NOT NULL,
    payload TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

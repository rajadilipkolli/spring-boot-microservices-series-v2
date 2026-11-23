-- Schema definition for processed_messages idempotency table
CREATE TABLE IF NOT EXISTS processed_messages (
    message_key VARCHAR(255) PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    result TEXT,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processed_messages_expires_at ON processed_messages(expires_at);

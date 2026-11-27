CREATE TABLE IF NOT EXISTS processed_order_events (
    order_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (order_id, event_type)
);

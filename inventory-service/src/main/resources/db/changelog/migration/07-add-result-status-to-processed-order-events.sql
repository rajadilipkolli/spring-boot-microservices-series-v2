--liquibase formatted sql
--preconditions onFail:MARK_RAN onError:HALT
--precondition-dbms type:postgresql
--changeset raja:07-add-result-status-to-processed-order-events
ALTER TABLE processed_order_events ADD COLUMN IF NOT EXISTS result_status VARCHAR(50);
ALTER TABLE processed_order_events ALTER COLUMN event_type TYPE VARCHAR(100);

--rollback ALTER TABLE processed_order_events DROP COLUMN IF EXISTS result_status;
--rollback ALTER TABLE processed_order_events ALTER COLUMN event_type TYPE VARCHAR(50);

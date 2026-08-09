--liquibase formatted sql

--changeset system:insert-sample-customers context:sample-data validCheckSum:ANY
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM payment.customers

INSERT INTO payment.customers (id, name, email, address, phone, amount_available, amount_reserved)
VALUES (401, 'John Doe', 'john.doe@example.com', '123 Main St, New York, NY 10001', '+1-555-0101', 5000.00, 200.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO payment.customers (id, name, email, address, phone, amount_available, amount_reserved)
VALUES (402, 'Jane Smith', 'jane.smith@example.com', '456 Oak Ave, Los Angeles, CA 90210', '+1-555-0102', 3500.00, 150.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO payment.customers (id, name, email, address, phone, amount_available, amount_reserved)
VALUES (403, 'Mike Johnson', 'mike.johnson@example.com', '789 Pine Rd, Chicago, IL 60601', '+1-555-0103', 7500.00, 500.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO payment.customers (id, name, email, address, phone, amount_available, amount_reserved)
VALUES (404, 'Sarah Wilson', 'sarah.wilson@example.com', '321 Elm St, Miami, FL 33101', '+1-555-0104', 2800.00, 100.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO payment.customers (id, name, email, address, phone, amount_available, amount_reserved)
VALUES (405, 'Retail Store', 'retailstore@gmail.com', '654 Maple Dr, Seattle, WA 98101', '+1-555-0105', 4200.00, 0.00)
ON CONFLICT (id) DO NOTHING;

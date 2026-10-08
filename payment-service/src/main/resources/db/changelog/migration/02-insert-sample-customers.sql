-- liquibase formatted sql

-- changeset system:insert-sample-customers runOnChange:true
INSERT INTO payment.customers (id, name, email, address, phone, amount_available, amount_reserved, version) VALUES
(101, 'John Doe', 'john.doe@example.com', '123 Main St, New York, NY 10001', '+1-555-0100', 5000.00, 200.00, 0),
(102, 'Jane Smith', 'jane.smith@example.com', '456 Oak Ave, Los Angeles, CA 90210', '+1-555-0101', 3500.00, 150.00, 0),
(103, 'Mike Johnson', 'mike.johnson@example.com', '789 Pine Rd, Chicago, IL 60601', '+1-555-0102', 7500.00, 500.00, 0),
(104, 'raja', 'rajakolli@gmail.com', '123 Raja St', '+1-555-0104', 10000.00, 0.00, 0),
(105, 'retail', 'retailstore@gmail.com', '123 Retail St', '+1-555-0105', 10000.00, 0.00, 0)
ON CONFLICT (id) DO NOTHING;

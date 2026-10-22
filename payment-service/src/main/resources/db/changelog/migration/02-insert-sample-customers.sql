-- liquibase formatted sql

-- changeset system:insert-sample-customers runOnChange:true
INSERT INTO payment.customers (id, name, email, address_line1, address_line2, city, state, zip_code, country, phone, amount_available, amount_reserved, version) VALUES
(101, 'John Doe', 'john.doe@example.com', '123 Main St', null, 'New York', 'NY', '10001', 'USA', '+1-555-0100', 5000.00, 200.00, 0),
(102, 'Jane Smith', 'jane.smith@example.com', '456 Oak Ave', null, 'Los Angeles', 'CA', '90210', 'USA', '+1-555-0101', 3500.00, 150.00, 0),
(103, 'Mike Johnson', 'mike.johnson@example.com', '789 Pine Rd', null, 'Chicago', 'IL', '60601', 'USA', '+1-555-0102', 7500.00, 500.00, 0),
(104, 'raja', 'rajakolli@gmail.com', '123 Raja St', null, 'Raja City', 'RA', '12345', 'USA', '+1-555-0104', 10000.00, 0.00, 0),
(105, 'retail', 'retailstore@gmail.com', '123 Retail St', null, 'Retail City', 'RE', '12345', 'USA', '+1-555-0105', 10000.00, 0.00, 0)
ON CONFLICT (id) DO NOTHING;

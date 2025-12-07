-- Manual Sample Data Insertion Script
-- Run this after services are started to populate with sample data

-- Sample Products (Catalog Service)
INSERT INTO products (product_code, product_name, description, price, image_url) VALUES
('P001', 'iPhone 15 Pro', 'Latest Apple iPhone with advanced camera system', 999.99, 'https://example.com/images/iphone15pro.jpg'),
('P002', 'Samsung Galaxy S24', 'Premium Android smartphone with AI features', 899.99, 'https://example.com/images/galaxy-s24.jpg'),
('P003', 'MacBook Air M3', 'Ultra-thin laptop with M3 chip', 1299.99, 'https://example.com/images/macbook-air-m3.jpg'),
('P004', 'Dell XPS 13', 'Premium Windows ultrabook', 1099.99, 'https://example.com/images/dell-xps13.jpg'),
('P005', 'AirPods Pro', 'Wireless earbuds with active noise cancellation', 249.99, 'https://example.com/images/airpods-pro.jpg')
ON CONFLICT (product_code) DO NOTHING;

-- Sample Inventory (Inventory Service)
INSERT INTO inventory (product_code, quantity, reserved_items) VALUES
('P001', 50, 5),
('P002', 30, 2),
('P003', 25, 3),
('P004', 40, 1),
('P005', 100, 10)
ON CONFLICT (product_code) DO NOTHING;

-- Sample Customers (Payment Service) - Using payment schema and avoiding existing IDs
INSERT INTO payment.customers (id, name, email, address, phone, amount_available, amount_reserved) VALUES
(401, 'retail', 'retail@example.com', '123 Main St, New York, NY 10001', '+1-555-0101', 5000.00, 200.00),
(402, 'Jane Smith', 'jane.smith@example.com', '456 Oak Ave, Los Angeles, CA 90210', '+1-555-0102', 3500.00, 150.00),
(403, 'Mike Johnson', 'mike.johnson@example.com', '789 Pine Rd, Chicago, IL 60601', '+1-555-0103', 7500.00, 500.00),
(404, 'Sarah Wilson', 'sarah.wilson@example.com', '321 Elm St, Miami, FL 33101', '+1-555-0104', 2800.00, 100.00),
(405, 'David Brown', 'david.brown@example.com', '654 Maple Dr, Seattle, WA 98101', '+1-555-0105', 4200.00, 300.00)
ON CONFLICT (id) DO NOTHING;

-- Sample Orders (Order Service) - Using customer IDs that exist (401-405)
-- Get the next order ID to avoid conflicts
DO $$
DECLARE
    next_order_id INTEGER;
BEGIN
    -- Get next available order ID
    SELECT COALESCE(MAX(id), 0) + 1 INTO next_order_id FROM orders;
    
    -- Insert orders with known IDs
    INSERT INTO orders (id, customer_id, status, source, delivery_address_line1, delivery_address_city, delivery_address_state, delivery_address_zip_code, delivery_address_country, created_by, created_date) VALUES
    (next_order_id, 401, 'NEW', 'WEB', '123 Main St', 'New York', 'NY', '10001', 'USA', 'system', '2024-01-15 10:30:00'),
    (next_order_id + 1, 402, 'CONFIRMED', 'MOBILE', '456 Oak Ave', 'Los Angeles', 'CA', '90210', 'USA', 'system', '2024-01-16 14:15:00'),
    (next_order_id + 2, 403, 'CONFIRMED', 'WEB', '789 Pine Rd', 'Chicago', 'IL', '60601', 'USA', 'system', '2024-01-10 09:45:00');
    
    -- Insert order items with correct order IDs
    INSERT INTO order_items (product_code, quantity, product_price, order_id) VALUES
    ('P001', 1, 999.99, next_order_id),
    ('P005', 2, 249.99, next_order_id),
    ('P003', 1, 1299.99, next_order_id + 1),
    ('P002', 1, 899.99, next_order_id + 2),
    ('P004', 1, 1099.99, next_order_id + 2);
    
    -- Update sequence to next available value
    PERFORM setval('orders_seq', next_order_id + 2);
END $$;
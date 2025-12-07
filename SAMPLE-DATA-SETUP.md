# 📊 Sample Data Setup Guide

This guide explains how to automatically populate the microservices with sample data using Liquibase.

## 🎯 Overview

Sample data has been added to all microservices to demonstrate the application functionality:

- **📚 Catalog Service**: 5 sample products (phones, laptops, accessories)
- **📦 Inventory Service**: Stock levels for all products
- **💳 Payment Service**: 5 sample customers with account balances
- **🛍️ Order Service**: 3 sample orders with order items

## 🚀 Enabling Sample Data

### ⚠️ **Important Note for Docker Images**

The current pre-built Docker images don't contain the sample data migration files. Use one of these methods:

### Method 1: Manual SQL Script (Quick Fix)

```bash
# After services are running
docker exec -i postgresql psql -U appuser -d appdb < deployment/manual-sample-data.sql
```

### Method 2: Rebuild Images with Sample Data

```bash
# Build new images that include sample data files
./mvnw clean install
docker-compose build
```

### Method 3: Using Liquibase Contexts (After Rebuild)

Add the following to your `application.properties`:

```properties
# Enable sample data loading
spring.liquibase.contexts=sample-data
```

### Method 2: Environment Variable

```bash
# Set environment variable
export SPRING_LIQUIBASE_CONTEXTS=sample-data

# Or in Docker Compose
environment:
  - SPRING_LIQUIBASE_CONTEXTS=sample-data
```

### Method 3: Application Startup

```bash
# Run with sample data context
java -jar app.jar --spring.liquibase.contexts=sample-data
```

## 📋 Sample Data Details

### 📚 Products (Catalog Service)
| Code | Name | Price | Description |
|------|------|-------|-------------|
| P001 | iPhone 15 Pro | $999.99 | Latest Apple iPhone |
| P002 | Samsung Galaxy S24 | $899.99 | Premium Android smartphone |
| P003 | MacBook Air M3 | $1299.99 | Ultra-thin laptop |
| P004 | Dell XPS 13 | $1099.99 | Premium Windows ultrabook |
| P005 | AirPods Pro | $249.99 | Wireless earbuds |

### 📦 Inventory Levels
| Product | Available | Reserved |
|---------|-----------|----------|
| P001 | 50 | 5 |
| P002 | 30 | 2 |
| P003 | 25 | 3 |
| P004 | 40 | 1 |
| P005 | 100 | 10 |

### 💳 Customers (Payment Service)
| ID | Name | Email | Available Balance |
|----|------|-------|------------------|
| 401 | John Doe | john.doe@example.com | $5,000.00 |
| 402 | Jane Smith | jane.smith@example.com | $3,500.00 |
| 403 | Mike Johnson | mike.johnson@example.com | $7,500.00 |
| 404 | Sarah Wilson | sarah.wilson@example.com | $2,800.00 |
| 405 | David Brown | david.brown@example.com | $4,200.00 |

**Note**: Sample customers use IDs 401-405 to avoid conflicts with existing data (IDs 1-400).

### 🛍️ Sample Orders
| Customer | Status | Items | Total Value |
|----------|--------|-------|-------------|
| John Doe | NEW | iPhone 15 Pro + 2x AirPods Pro | $1,499.97 |
| Jane Smith | CONFIRMED | MacBook Air M3 | $1,299.99 |
| Mike Johnson | CONFIRMED | Galaxy S24 + Dell XPS 13 | $1,999.98 |

## 🔧 Configuration Files

### Catalog Service
```yaml
# File: catalog-service/src/main/resources/db/changelog/migration/02-insert-sample-products.yaml
databaseChangeLog:
  - changeSet:
      id: insert-sample-products
      author: system
      context: sample-data
```

### Inventory Service
```json
// File: inventory-service/src/main/resources/db/changelog/migration/02-insert-sample-inventory.json
{
  "databaseChangeLog": [
    {
      "changeSet": {
        "id": "insert-sample-inventory",
        "author": "system",
        "context": "sample-data"
      }
    }
  ]
}
```

### Payment Service
```xml
<!-- File: payment-service/src/main/resources/db/changelog/migration/02-insert-sample-customers.xml -->
<changeSet author="system" id="insert-sample-customers" context="sample-data">
```

### Order Service
```xml
<!-- File: order-service/src/main/resources/db/changelog/migration/02-insert-sample-orders.xml -->
<changeSet author="system" id="insert-sample-orders" context="sample-data">
```

## 🚫 Disabling Sample Data

### For Production Environments

**Do NOT set the context** or explicitly exclude it:

```properties
# Production - no sample data
# spring.liquibase.contexts=production

# Or exclude sample data context
spring.liquibase.contexts=!sample-data
```

## 🔄 Rebuilding with Sample Data

### Clean Database and Reload

```bash
# Stop services
docker-compose down

# Remove database volume
docker volume rm spring-boot-microservices-series-v2_postgres_data

# Start with sample data context
SPRING_LIQUIBASE_CONTEXTS=sample-data docker-compose up -d
```

### Individual Service Testing

```bash
# Test catalog service with sample data
cd catalog-service
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.liquibase.contexts=sample-data"
```

## ✅ Verification

### Check Data Loading

```sql
-- Connect to PostgreSQL
docker exec -it postgresql psql -U appuser -d appdb

-- Verify sample data
SELECT COUNT(*) FROM products;     -- Should return 5
SELECT COUNT(*) FROM inventory;    -- Should return 5  
SELECT COUNT(*) FROM customers;    -- Should return 5
SELECT COUNT(*) FROM orders;       -- Should return 3
SELECT COUNT(*) FROM order_items;  -- Should return 5
```

### API Verification

```bash
# Check products via API Gateway
curl http://localhost:8765/catalog/api/products

# Check inventory
curl http://localhost:8765/inventory/api/inventory

# Check customers  
curl http://localhost:8765/payment/api/customers
```

## 🎯 Best Practices

1. **Development**: Always use sample data contexts
2. **Testing**: Use sample data for integration tests
3. **Production**: Never enable sample data contexts
4. **CI/CD**: Use environment-specific configurations

## 🔍 Troubleshooting

### Sample Data Not Loading

1. **Check Context**: Ensure `spring.liquibase.contexts=sample-data` is set
2. **Check Logs**: Look for Liquibase execution logs
3. **Verify Files**: Ensure all migration files exist in `/migration` folders
4. **Database State**: Check if changesets were already executed

### Duplicate Data Errors

```bash
# Clear Liquibase history and reload
DELETE FROM databasechangelog WHERE id LIKE '%sample%';
```

---

*Sample data provides a complete demonstration environment for testing all microservice interactions.*
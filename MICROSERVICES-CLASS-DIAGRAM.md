# 🏗️ Microservices Class Diagram

This document shows the entity relationships across all microservices in the Spring Boot Microservices Series project.

## 📊 Entity Relationship Diagram

```mermaid
classDiagram
    %% Catalog Service Domain
    class Product {
        +Long id
        +String productCode
        +String productName
        +String description
        +double price
        +String imageUrl
        +getId() Long
        +setProductCode(String) Product
        +getProductCode() String
        +setPrice(double) Product
    }

    %% Inventory Service Domain
    class Inventory {
        +Long id
        +String productCode
        +Integer availableQuantity
        +Integer reservedItems
        +Short version
        +getId() Long
        +setProductCode(String) Inventory
        +getAvailableQuantity() Integer
        +setReservedItems(Integer) Inventory
    }

    %% Order Service Domain
    class Order {
        +Long id
        +Long customerId
        +OrderStatus status
        +String source
        +Address deliveryAddress
        +Short version
        +List~OrderItem~ items
        +addOrderItem(OrderItem) void
        +removeOrderItem(OrderItem) void
        +setCustomerId(Long) Order
    }

    class OrderItem {
        +Long id
        +String productCode
        +int quantity
        +BigDecimal productPrice
        +Order order
        +setProductCode(String) OrderItem
        +setQuantity(int) OrderItem
        +setOrder(Order) OrderItem
    }

    class OrderStatus {
        <<enumeration>>
        NEW
        CONFIRMED
        SHIPPED
        DELIVERED
        CANCELLED
    }

    class Address {
        +String addressLine1
        +String addressLine2
        +String city
        +String state
        +String zipCode
        +String country
    }

    class Auditable {
        <<abstract>>
        +String createdBy
        +LocalDateTime createdDate
        +String lastModifiedBy
        +LocalDateTime lastModifiedDate
    }

    %% Payment Service Domain
    class Customer {
        +Long id
        +String name
        +String email
        +String address
        +String phone
        +double amountAvailable
        +double amountReserved
        +setAmountAvailable(double) Customer
        +setAmountReserved(double) Customer
    }

    %% Relationships
    Order --> OrderItem
    Order --> Address
    Order --> OrderStatus
    Order --> Auditable
    Product --> Inventory
    Product --> OrderItem
    Customer --> Order

    %% Service Boundaries
    note for Product "📚 Catalog Service\nPort: 18080\nDatabase: products table"
    note for Inventory "📦 Inventory Service\nPort: 18181\nDatabase: inventory table"
    note for Order "🛍️ Order Service\nPort: 18282\nDatabase: orders, order_items tables"
    note for Customer "💳 Payment Service\nPort: 18085\nDatabase: customers table"
```

## 🏢 Service Ownership

### 📚 Catalog Service (Port: 18080)
- **Entities**: `Product`
- **Database**: `products` table
- **Responsibility**: Product catalog management
- **Technology**: Spring Data R2DBC + PostgreSQL

### 📦 Inventory Service (Port: 18181)
- **Entities**: `Inventory`
- **Database**: `inventory` table
- **Responsibility**: Stock level management
- **Technology**: Spring Data JPA + PostgreSQL

### 🛍️ Order Service (Port: 18282)
- **Entities**: `Order`, `OrderItem`, `OrderStatus`, `Address`, `Auditable`
- **Database**: `orders`, `order_items` tables
- **Responsibility**: Order processing and orchestration
- **Technology**: Spring Data JPA + PostgreSQL

### 💳 Payment Service (Port: 18085)
- **Entities**: `Customer`
- **Database**: `customers` table
- **Responsibility**: Customer and payment management
- **Technology**: Spring Data + PostgreSQL

## 🔗 Cross-Service Relationships

### Logical Relationships (via productCode)
```mermaid
graph LR
    A[Product.productCode] -.->|references| B[Inventory.productCode]
    A -.->|references| C[OrderItem.productCode]
    D[Customer.id] -.->|references| E[Order.customerId]
    
    style A fill:#e1f5fe
    style B fill:#f3e5f5
    style C fill:#fff3e0
    style D fill:#e8f5e8
    style E fill:#fff3e0
```

### Data Consistency
- **Product-Inventory**: Linked by `productCode` (String)
- **Product-OrderItem**: Linked by `productCode` (String)
- **Customer-Order**: Linked by `customerId` (Long)
- **Order-OrderItem**: JPA relationship with foreign key

## 🗄️ Database Schema Summary

| Service | Tables | Key Fields | Relationships |
|---------|--------|------------|---------------|
| **Catalog** | `products` | `id`, `product_code`, `product_name`, `price` | None (referenced by others) |
| **Inventory** | `inventory` | `id`, `product_code`, `quantity`, `reserved_items` | References Product via `product_code` |
| **Order** | `orders`, `order_items` | `orders.id`, `order_items.product_code` | `order_items` → `orders` (FK) |
| **Payment** | `customers` | `id`, `name`, `email`, `amount_available` | Referenced by Order via `customer_id` |

## 🚀 Communication Patterns

### Synchronous (REST API)
- Order Service → Catalog Service (product details)
- Order Service → Inventory Service (stock check)
- Order Service → Payment Service (customer validation)

### Asynchronous (Kafka Events)
- Order events for inventory updates
- Payment events for order confirmation
- Inventory events for stock notifications

---

*This diagram represents the current microservices architecture with clear service boundaries and entity ownership.*
# Saga Sequence Diagram: Inventory Service

This diagram illustrates the two-phase distributed reservation saga between `order-service`, `inventory-service`, and `payment-service` mediated via Apache Kafka. Correct runtime topic names (`orders`, `stock-orders`, `-retry-inventory`, `-dlt-inventory`) are used.

```mermaid
sequenceDiagram
    autonumber
    participant OrderSvc as Order Service
    participant KafkaOrders as Kafka (orders)
    participant InvConsumer as Inventory Consumer (stock)
    participant InvDB as Inventory DB (appdb)
    participant ModulithOutbox as Modulith Outbox
    participant KafkaStock as Kafka (stock-orders)
    participant OrderStreams as Order KafkaStreams

    Note over OrderSvc, KafkaOrders: Phase 1: Order Placement & Stock Reservation
    OrderSvc->>KafkaOrders: Publish OrderDto (status: NEW, orderId: 101, attemptId: "att-1")
    KafkaOrders->>InvConsumer: Consume record
    InvConsumer->>InvDB: INSERT ON CONFLICT DO NOTHING into processed_order_events (101, "RESERVE")
    
    alt Stock Available & Active
        InvDB-->>InvConsumer: Claim Successful (1 row)
        InvConsumer->>InvDB: UPDATE inventory: decrease quantity, increase reserved_items
        InvConsumer->>InvDB: UPDATE processed_order_events: result_status = "ACCEPT"
        InvConsumer->>InvDB: INSERT INTO event_publication (topic: stock-orders, status: ACCEPT)
        InvDB-->>ModulithOutbox: Commit local transaction
        ModulithOutbox->>KafkaStock: Publish flat OrderDto (status: ACCEPT, source: INVENTORY)
    else Insufficient Stock or Missing Product
        InvConsumer->>InvDB: UPDATE processed_order_events: result_status = "REJECT"
        InvConsumer->>InvDB: INSERT INTO event_publication (topic: stock-orders, status: REJECT)
        ModulithOutbox->>KafkaStock: Publish flat OrderDto (status: REJECT, source: INVENTORY)
    else Processing Failure / Retries
        InvConsumer->>KafkaOrders: Non-blocking retry to orders-retry-inventory-0
        Note over InvConsumer: Max attempts exceeded -> orders-dlt-inventory
    end

    Note over OrderStreams, KafkaStock: Phase 2: Correlation & Outcome Finalization
    KafkaStock->>OrderStreams: Consume stock-orders
    OrderStreams->>OrderStreams: Correlate with payment-orders stream by attempt (30s window)

    alt Both Services Accepted
        OrderStreams->>KafkaOrders: Publish OrderDto (status: CONFIRMED, orderId: 101)
        KafkaOrders->>InvConsumer: Consume CONFIRMED record
        InvConsumer->>InvDB: Claim (101, "CONFIRM_CONFIRMED")
        InvConsumer->>InvDB: Apply ConfirmedOrderStockActionStrategy: decrease reserved_items
    else Any Service Rejected / Rollback
        OrderStreams->>KafkaOrders: Publish OrderDto (status: ROLLBACK, orderId: 101)
        KafkaOrders->>InvConsumer: Consume ROLLBACK record
        InvConsumer->>InvDB: Claim (101, "CONFIRM_ROLLBACK")
        InvConsumer->>InvDB: Apply RollbackOrderStockActionStrategy: decrease reserved_items, increase quantity
    end
```

# 🌊 Kafka Streams Fundamentals & Architecture

## 📖 Table of Contents
1. [What is Kafka Streams?](#what-is-kafka-streams)
2. [Core Concepts](#core-concepts)
3. [Why Kafka Streams vs Regular Kafka?](#why-kafka-streams-vs-regular-kafka)
4. [Stream vs Consumer Differences](#stream-vs-consumer-differences)
5. [Order Service Implementation](#order-service-implementation)
6. [Stream Processing Topology](#stream-processing-topology)

## 🎯 What is Kafka Streams?

Kafka Streams is a **client library** for building applications and microservices where input and output data are stored in Kafka clusters. It combines the simplicity of writing and deploying standard Java applications with the benefits of Kafka's server-side cluster technology.

### Key Characteristics
- **Library, not framework** - Embedded in your application
- **No external dependencies** - Just Kafka cluster required
- **Fault tolerant** - Automatic recovery and rebalancing
- **Scalable** - Horizontal scaling through partitioning
- **Exactly-once processing** - Strong consistency guarantees

## 🏗️ Core Concepts

### 1. Stream Processing Topology

```mermaid
flowchart TD
    subgraph "🔄 Stream Processing Topology"
        SOURCE1[📥 Source: payment-orders]
        SOURCE2[📥 Source: stock-orders]
        
        STREAM1[🌊 KStream: Payment Events]
        STREAM2[🌊 KStream: Stock Events]
        
        JOIN[🔗 Stream Join]
        WINDOW[⏰ 30s Window + 30s Grace]
        
        PROCESSOR[⚙️ Business Logic Processor]
        
        SINK1[📤 Sink: orders]
        SINK2[📤 Sink: recovererDLQ]
        
        STORE[💾 State Store]
    end
    
    SOURCE1 --> STREAM1
    SOURCE2 --> STREAM2
    
    STREAM1 --> JOIN
    STREAM2 --> JOIN
    JOIN --> WINDOW
    WINDOW --> PROCESSOR
    
    PROCESSOR --> SINK1
    PROCESSOR --> SINK2
    
    SINK1 --> STORE
    
    style JOIN fill:#ec4899,color:#ffffff
    style WINDOW fill:#f59e0b,color:#ffffff
    style PROCESSOR fill:#22c55e,color:#ffffff
    style STORE fill:#8b5cf6,color:#ffffff
```

### 2. KStream vs KTable

#### **KStream - Event Stream**
```java
// Represents a stream of records (insert-only)
KStream<Long, OrderDto> orderEvents = builder.stream("orders");

// Each record is an independent event
// Example: Order created, Order updated, Order cancelled
```

#### **KTable - State Table**
```java
// Represents a changelog stream (upsert semantics)
KTable<Long, OrderDto> orderState = orderEvents.toTable();

// Latest value for each key
// Example: Current order status per order ID
```

### 3. Stateful vs Stateless Operations

#### **Stateless Operations** (No local state)
```java
stream.filter(order -> order.amount() > 100)     // Filter
      .map(order -> order.withDiscount(0.1))     // Transform
      .peek((k,v) -> log.info("Processing: {}", v)); // Side effect
```

#### **Stateful Operations** (Maintain local state)
```java
stream.groupByKey()
      .aggregate(() -> 0.0,                      // Initial value
                (k, order, total) -> total + order.amount(), // Aggregator
                Materialized.as("order-totals"));  // State store
```

## 🤔 Why Kafka Streams vs Regular Kafka?

### Architecture Comparison

```mermaid
graph TB
    subgraph "❌ Traditional Kafka Consumer Approach"
        subgraph "Service Instance 1"
            C1[Consumer 1]
            CACHE1[Manual Cache]
            DB1[(Database)]
        end
        
        subgraph "Service Instance 2"
            C2[Consumer 2]
            CACHE2[Manual Cache]
            DB2[(Database)]
        end
        
        subgraph "Kafka Topics"
            PT[payment-orders]
            ST[stock-orders]
        end
        
        PT --> C1
        ST --> C1
        PT --> C2
        ST --> C2
        
        C1 --> CACHE1
        C1 --> DB1
        C2 --> CACHE2
        C2 --> DB2
        
        CACHE1 -.->|Manual Correlation| CACHE1
        CACHE2 -.->|Manual Correlation| CACHE2
    end
    
    subgraph "✅ Kafka Streams Approach"
        subgraph "Stream Instance 1"
            KS1[Kafka Streams]
            SS1[State Store]
            RDB1[(RocksDB)]
        end
        
        subgraph "Stream Instance 2"
            KS2[Kafka Streams]
            SS2[State Store]
            RDB2[(RocksDB)]
        end
        
        subgraph "Kafka Topics"
            PT2[payment-orders]
            ST2[stock-orders]
            OT2[orders]
        end
        
        PT2 --> KS1
        ST2 --> KS1
        PT2 --> KS2
        ST2 --> KS2
        
        KS1 --> SS1
        KS1 --> OT2
        KS2 --> SS2
        KS2 --> OT2
        
        SS1 --> RDB1
        SS2 --> RDB2
        
        KS1 -.->|Auto Join & Window| KS1
        KS2 -.->|Auto Join & Window| KS2
    end
    
    style C1 fill:#ef4444,color:#ffffff
    style C2 fill:#ef4444,color:#ffffff
    style KS1 fill:#22c55e,color:#ffffff
    style KS2 fill:#22c55e,color:#ffffff
```

### Traditional Kafka Consumer Problems

```java
@KafkaListener(topics = "payment-orders")
public void handlePayment(OrderDto payment) {
    // ❌ Problems:
    // 1. Manual correlation with stock events
    // 2. Complex state management
    // 3. No built-in windowing
    // 4. Manual offset management
    // 5. No exactly-once guarantees
    
    // Store in database/cache for correlation
    paymentCache.put(payment.orderId(), payment);
    
    // Check if corresponding stock event exists
    OrderDto stock = stockCache.get(payment.orderId());
    if (stock != null) {
        processOrder(payment, stock);
        // Manual cleanup
        paymentCache.remove(payment.orderId());
        stockCache.remove(payment.orderId());
    }
    // What if stock event never arrives?
    // What if this instance crashes?
    // How to handle duplicates?
}
```

### Kafka Streams Solution

```java
// ✅ Advantages:
// 1. Automatic event correlation
// 2. Built-in windowing and time semantics
// 3. Exactly-once processing
// 4. Automatic state management
// 5. Fault tolerance and recovery

correlateByAttempt(paymentStream)
    .join(correlateByAttempt(stockStream),
          orderManageService::confirm,                    // Business logic only
          JoinWindows.ofTimeDifferenceAndGrace(Duration.ofSeconds(30), Duration.ofSeconds(30)),
          StreamJoined.with(Serdes.String(), orderSerde, orderSerde))
    .filter((key, order) -> order != null)
    .selectKey((key, order) -> order.orderId().toString())
    .to("orders", Produced.with(Serdes.String(), orderSerde));
```

## ⚖️ Stream vs Consumer Differences

| Aspect | Kafka Consumer | Kafka Streams |
|--------|----------------|---------------|
| **Processing Model** | Pull-based, manual polling | Push-based, declarative |
| **State Management** | Manual (DB/Cache) | Built-in (RocksDB) |
| **Windowing** | Manual implementation | Built-in time windows |
| **Joins** | Manual correlation | Native stream joins |
| **Exactly-Once** | Manual deduplication | Built-in EXACTLY_ONCE_V2 |
| **Scaling** | Consumer groups | Stream threads + partitions |
| **Fault Tolerance** | Manual offset management | Automatic checkpointing |
| **Latency** | Lower (direct processing) | Slightly higher (processing overhead) |
| **Complexity** | High (manual orchestration) | Low (declarative topology) |

### When to Use Each?

#### **Use Kafka Consumer when:**
- Simple event processing
- Direct database writes
- Low latency requirements (< 10ms)
- Simple business logic
- No event correlation needed

#### **Use Kafka Streams when:**
- Event correlation needed ✅ **Our use case**
- Complex transformations
- Windowed operations ✅ **30-second join window (+ 30s grace)**
- Stateful processing ✅ **Order state management**
- Exactly-once processing ✅ **Financial transactions**

## 🛍️ Order Service Implementation

### Business Problem
The Order Service needs to:
1. **Receive order creation** from REST API
2. **Wait for payment processing** result (ACCEPT/REJECT)
3. **Wait for inventory reservation** result (ACCEPT/REJECT)
4. **Correlate both results** within a time window
5. **Apply business logic** to determine final order status
6. **Update order status** and notify other services

### Why Kafka Streams is Perfect Here

```mermaid
sequenceDiagram
    participant API as 🛍️ Order API
    participant Payment as 💳 Payment Service
    participant Inventory as 📦 Inventory Service
    participant Streams as 🌊 Kafka Streams
    participant DB as 🗄️ Database
    
    Note over API, DB: Order Processing Flow
    
    API->>API: Create order (status: NEW)
    API->>Streams: Publish to 'orders' topic
    
    par Payment Processing
        Streams->>Payment: Order event
        Payment->>Payment: Process payment
        Payment->>Streams: Publish to 'payment-orders' (ACCEPT/REJECT)
    and Inventory Processing
        Streams->>Inventory: Order event
        Inventory->>Inventory: Reserve stock
        Inventory->>Streams: Publish to 'stock-orders' (ACCEPT/REJECT)
    end
    
    Note over Streams: Join payment + stock events within 30s window + 30s grace
    
    Streams->>Streams: Apply business logic:<br/>ACCEPT+ACCEPT=CONFIRMED<br/>REJECT+REJECT=REJECTED<br/>ACCEPT+REJECT=ROLLBACK
    
    Streams->>DB: Update order status & source
    Streams->>Streams: Publish final result to 'orders'
```

### Stream Processing Benefits

1. **Automatic Correlation**: Correlates by `orderId:attemptId` to avoid stale retry collisions
2. **Time Windows**: 30-second window with 30-second grace period for event correlation
3. **Exactly-Once**: Prevents duplicate order processing via `EXACTLY_ONCE_V2`
4. **Fault Tolerance**: Automatic recovery from failures via RocksDB & changelog topics
5. **Scalability**: Horizontal scaling through partitioning

## 🔄 Stream Processing Topology

### Complete Topology Implementation
**File:** `KafkaStreamsConfig.java`

```java
@Bean
KStream<String, OrderDto> stream(
        StreamsBuilder kafkaStreamBuilder, Serde<@NonNull OrderDto> orderSerde) {

    KStream<String, OrderDto> paymentStream =
            kafkaStreamBuilder.stream(
                    PAYMENT_ORDERS_TOPIC, Consumed.with(Serdes.String(), orderSerde));

    correlateByAttempt(paymentStream)
            .join(
                    correlateByAttempt(
                            kafkaStreamBuilder.stream(
                                    STOCK_ORDERS_TOPIC,
                                    Consumed.with(Serdes.String(), orderSerde))),
                    orderManageService::confirm,
                    JoinWindows.ofTimeDifferenceAndGrace(
                            Duration.ofSeconds(30), Duration.ofSeconds(30)),
                    StreamJoined.with(Serdes.String(), orderSerde, orderSerde))
            .filter((key, order) -> order != null)
            .selectKey((key, order) -> order.orderId().toString())
            .peek((k, o) -> log.debug("Output of Stream : {} for key :{}", o, k))
            .to(ORDERS_TOPIC, Produced.with(Serdes.String(), orderSerde));

    paymentStream.print(Printed.toSysOut());

    return paymentStream;
}

private KStream<String, OrderDto> correlateByAttempt(KStream<String, OrderDto> stream) {
    return stream.filter(
                    (key, order) ->
                            order != null
                                    && order.orderId() != null
                                    && order.attemptId() != null
                                    && !order.attemptId().isBlank())
            .selectKey((key, order) -> order.orderId() + ":" + order.attemptId());
}
```

### Dead Letter Queue Recoverer

```java
@Bean
DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaProperties properties) {
    Map<String, Object> props = properties.buildProducerProperties();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

    DefaultKafkaProducerFactory<byte[], byte[]> factory =
            new DefaultKafkaProducerFactory<>(props);

    return new DeadLetterPublishingRecoverer(
            new KafkaTemplate<>(factory),
            (record, ex) -> new TopicPartition(RECOVER_DLQ_TOPIC, -1));
}
```

## 🎯 Key Takeaways

### Why Kafka Streams for Order Service?

1. **Event Correlation**: Automatically correlates payment and inventory events by `orderId:attemptId`
2. **Time Windows**: Built-in 30-second window with 30-second grace period for event matching
3. **Business Logic**: Clean separation of stream processing and business rules in `OrderManageService`
4. **State Management**: Automatic state store management with RocksDB
5. **Exactly-Once**: Critical for financial transaction processing (`EXACTLY_ONCE_V2`)
6. **Scalability**: Horizontal scaling through partition assignment
7. **Fault Tolerance**: Automatic recovery, rebalancing, and DLQ handling via `recovererDLQ`

### Architecture Benefits

- **Declarative**: Topology describes what to do, not how
- **Composable**: Easy to add new streams and processors
- **Testable**: TopologyTestDriver for unit testing
- **Observable**: Built-in metrics and monitoring
- **Maintainable**: Clear separation of concerns

This foundation enables sophisticated event-driven processing with minimal complexity, making it ideal for the Order Service's requirements of correlating multiple asynchronous events within time constraints while maintaining strong consistency guarantees.

---

**Next**: [Configuration & Setup](./02-configuration-and-setup.md) - Learn about critical configuration parameters and performance tuning.
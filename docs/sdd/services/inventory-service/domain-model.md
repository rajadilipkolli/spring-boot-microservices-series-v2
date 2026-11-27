# Domain Model Specification: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`

---

## 1. Ubiquitous Language Glossary

| Term | Definition | Context |
| :--- | :--- | :--- |
| **Inventory** | Persistent record tracking available stock quantity and reserved quantity for a distinct product code. | Core Domain |
| **Available Quantity** | Unreserved physical stock quantity available to fulfill new customer orders (`quantity`). | Inventory State |
| **Reserved Items** | Quantity of stock temporarily placed on hold for an order in progress, awaiting confirmation or rollback (`reserved_items`). | Reservation State |
| **Soft Deletion** | Logical retirement of an inventory record indicated by `deleted = true`, hiding it from active queries while preserving history. | Lifecycle |
| **Processed Order Event** | Persistent idempotent claim recording that a specific order ID and event type combination was executed. | Deduplication |
| **Composite Event Key** | Tuple `(order_id, event_type)` acting as primary key in `processed_order_events` to guard against duplicate message processing. | Concurrency |
| **Inventory Source Marker** | Constant string `"INVENTORY"` attached to outbound order events identifying that Inventory Service processed or authored the event. | Integration |
| **Attempt ID** | Unique UUID string generated per saga retry attempt, used in composite key `RESERVE_REPLY:<attemptId>` to ensure reply replay safety. | Saga Protocol |

---

## 2. Domain Entities & Persistence Mappings

### 2.1 Entity: `Inventory`
- **Class Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/Inventory.java#Inventory`
- **Database Table**: `inventory`
- **Primary Key**: `id` (`Long`), generated using Hypersistence TSID (`@Tsid(TsidFactorySupplier.class)`).
- **Natural Key**: `product_code` (`String`), unique constraint `inventory_product_code_key`.
- **Concurrency Control**: Enforced in JPA via `@Version` with `version` (`Short`).
- **Soft Deletion**: Governed by `deleted` (`boolean`), default `false`.

#### Field Definitions
| Field | Type | Column | Nullable | Default | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | No | TSID generated | Unique 64-bit TSID identifier. |
| `productCode` | `String` | `product_code` | No | None | Unique product code identifier. Updatable: `false` in JPA. |
| `availableQuantity` | `Integer` | `quantity` | Yes | `0` | Available stock for new reservations. |
| `reservedItems` | `Integer` | `reserved_items` | Yes | `0` | Quantity currently reserved by active orders. |
| `deleted` | `boolean` | `deleted` | No | `false` | Soft-deletion indicator flag. |
| `version` | `Short` | `version` | Yes | `0` | Optimistic locking version number. |

> [!WARNING]
> **No Database Non-Negative Stock Check**: As evidenced by Liquibase migration `01-create_inventories_table.json`, there is **no check constraint** in the PostgreSQL schema preventing `quantity` or `reserved_items` from becoming negative. Non-negative stock rules are enforced strictly in Java application logic.

---

### 2.2 Entity: `ProcessedOrderEvent`
- **Class Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/ProcessedOrderEvent.java#ProcessedOrderEvent`
- **Database Table**: `processed_order_events`
- **Composite Primary Key**: `ProcessedOrderEventId` (`orderId`, `eventType`)
- **Key Class**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/ProcessedOrderEventId.java#ProcessedOrderEventId`

#### Field Definitions
| Field | Type | Column | Nullable | Description |
| :--- | :--- | :--- | :--- | :--- |
| `orderId` | `Long` | `order_id` | No | External unique order identifier. |
| `eventType` | `String` | `event_type` | No | Deduplication marker: `RESERVE`, `RESERVE_REPLY:<attemptId>`, `CONFIRM_<status>`. |
| `processedAt` | `Instant` | `processed_at` | No | UTC timestamp when the claim was recorded. |
| `resultStatus` | `String` | `result_status` | Yes | Stored outcome of reservation (`ACCEPT`, `REJECT`). |

---

## 3. Data Transfer Objects (DTOs)

The service utilizes six primary DTO records:

### 3.1 `InventoryRequest`
- **Class Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/domain/models/InventoryRequest.java#InventoryRequest`
- **Usage**: Request body for creating or updating inventory records.
- **Fields**:
  - `productCode` (`String`): `@NotBlank(message = "ProductCode can't be blank")`
  - `availableQuantity` (`Integer`): `@PositiveOrZero(message = "Quantity can't be negative")` (nullable in record).

### 3.2 `InventoryResponse`
- **Class Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/domain/models/InventoryResponse.java#InventoryResponse`
- **Usage**: Outbound representation of an inventory item.
- **Fields**:
  - `id` (`Long`): Formatted as string (`@JsonFormat(shape = JsonFormat.Shape.STRING)`).
  - `productCode` (`String`): Product natural key.
  - `availableQuantity` (`Integer`): Current unreserved stock.
  - `reservedItems` (`Integer`): Current reserved stock.

### 3.3 `PagedResult<T>`
- **Class Reference**: `com.example.common.dto.PagedResult` (from `microservices-common:0.1.5`)
- **Usage**: Standard container for paginated query responses.
- **Fields** (8 fields total):
  - `data` (`List<T>`): Page items.
  - `totalElements` (`long`): Total elements matching query.
  - `pageNumber` (`int`): **One-based** page number (`page.getNumber() + 1`).
  - `totalPages` (`int`): Total available pages.
  - `isFirst` (`boolean`): True if current page is the first page.
  - `isLast` (`boolean`): True if current page is the last page.
  - `hasNext` (`boolean`): True if next page exists.
  - `hasPrevious` (`boolean`): True if previous page exists.

### 3.4 `OrderDto`
- **Class Reference**: `com.example.common.dto.OrderDto` (from `microservices-common:0.1.5`)
- **Usage**: Kafka event payload received from `orders` and published to `stock-orders`.
- **Fields**:
  - `orderId` (`Long`): Unique identifier of the order.
  - `customerId` (`Long`): Identifier of customer.
  - `status` (`String`): Plain-string order status (`NEW`, `ACCEPT`, `REJECT`, `CONFIRMED`, `ROLLBACK`).
  - `source` (`String`): Source service marking the event (e.g., `INVENTORY`).
  - `items` (`List<OrderItemDto>`): List of requested line items.
  - `attemptId` (`String`): Attempt UUID correlation string.

### 3.5 `OrderDto.OrderItemDto`
- **Class Reference**: `com.example.common.dto.OrderDto.OrderItemDto` (from `microservices-common:0.1.5`)
- **Usage**: Line item representation in `OrderDto`.
- **Fields**:
  - `itemId` (`Long`): Line item identifier.
  - `productId` (`String`): Product code matching `Inventory.productCode`.
  - `productPrice` (`BigDecimal`): Unit price.
  - `quantity` (`int`): Quantity ordered.

### 3.6 `ProductDto`
- **Class Reference**: `com.example.common.dto.ProductDto` (from `microservices-common:0.1.5`)
- **Usage**: Inbound product catalog event payload received from `productTopic`.
- **Fields**:
  - `id` (`Long`): Product catalog ID.
  - `code` (`String`): Unique product code matching `Inventory.productCode`.
  - `productName` (`String`): Name of the product.
  - `description` (`String`): Product description.
  - `price` (`double`): Unit price.
  - `isDeleted` (`boolean`): Deletion flag from catalog service.

---

## 4. Domain Invariants and Integrity Rules

- **Active vs Soft-Deleted Filtering**: Public queries and reservation workflows filter out soft-deleted inventory (`deleted = false`).
- **Dual Stock Movement**:
  - Reservation: $availableQuantity \leftarrow availableQuantity - quantity$, $reservedItems \leftarrow reservedItems + quantity$.
  - Confirmation: $reservedItems \leftarrow reservedItems - quantity$.
  - Rollback: $reservedItems \leftarrow reservedItems - quantity$, $availableQuantity \leftarrow availableQuantity + quantity$.
- **Absence of Retention / Cleanup**: There is no database or background scheduled cleanup rule to expire historical rows in `processed_order_events`, `event_publication_archive`, or soft-deleted `inventory`.

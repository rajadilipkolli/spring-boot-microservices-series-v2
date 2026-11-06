# Functional Requirements Specification: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`

This document details all functional requirements implemented by `inventory-service`, complete with Given/When/Then acceptance scenarios grounded in source code and test suites.

---

## 1. Functional Requirements Index

| ID | Title | Lifecycle Status | Evidence Status | Primary Source Reference |
| :--- | :--- | :--- | :--- | :--- |
| `INV-FR-001` | Paginated Active Inventory Retrieval | Approved | Verified | `InventoryController#getAllInventories` |
| `INV-FR-002` | Single Product Inventory Retrieval with Optional Delay | Approved | Verified | `InventoryController#getInventoryByProductCode` |
| `INV-FR-003` | Bulk Inventory Retrieval by Product Codes | Approved | Verified | `InventoryController#getInventoryByProductCodes` |
| `INV-FR-004` | Inventory Creation and Deleted Record Restoration | Approved | Verified | `InventoryController#createInventory` |
| `INV-FR-005` | Inventory Update by Identifier and Code | Approved | Verified | `InventoryController#updateInventory` |
| `INV-FR-006` | Soft Deletion of Inventory Records | Approved | Verified | `InventoryController#deleteInventory` |
| `INV-FR-007` | Batch Inventory Random Generation with Idempotency Key | Approved | Verified | `InventoryController#updateInventoryWithRandomValue` |
| `INV-FR-008` | Event-Driven Order Reservation and Confirmation Handling | Approved | Verified | `KafkaListenerConfig#onEvent` |

---

## 2. Requirement Specifications

### INV-FR-001: Paginated Active Inventory Retrieval

- **ID**: `INV-FR-001`
- **Category**: Functional Requirement
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Description**: Returns a sorted, paginated collection of active inventory records wrapped in `PagedResult<InventoryResponse>`. Records marked as soft-deleted (`deleted = true`) are strictly excluded.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/controllers/InventoryController.java#InventoryController#getAllInventories`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/controllers/InventoryControllerIT.java#shouldFetchAllInventories`
- **Linked Business Rules**: `INV-BR-001`, `INV-BR-003`
- **Linked Questions**: `INV-Q-002` (Inferred 500 on invalid sort properties)

#### Scenarios & Acceptance Criteria
##### Scenario 1: Retrieve Default Paginated Inventory (Happy Path)
- **Given** Active inventory records exist in PostgreSQL.
- **When** `GET /api/inventory` is invoked without query parameters.
- **Then** Return HTTP 200 with `pageNo=0`, `pageSize=10`, sorted by `id asc`, `pageNumber=1`, and data containing only records where `deleted = false`.

##### Scenario 2: Request Paging with Case-Insensitive Sort Direction
- **Given** Multiple active inventory records.
- **When** `GET /api/inventory?pageNo=1&pageSize=5&sortBy=availableQuantity&sortDir=DESC` is called.
- **Then** Return HTTP 200 containing up to 5 items sorted descending by available quantity, with `pageNumber=2`. Any sort direction string other than `asc` (case-insensitive) sorts descending.

---

### INV-FR-002: Single Product Inventory Retrieval with Optional Delay

- **ID**: `INV-FR-002`
- **Category**: Functional Requirement
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Description**: Retrieves active inventory for a specific product code. Supports an optional `delay` parameter in seconds to simulate latency (e.g. for testing circuit breakers or timeouts). The `delay` parameter is unbounded.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/controllers/InventoryController.java#InventoryController#getInventoryByProductCode`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/controllers/InventoryControllerIT.java#shouldFindInventoryByProductCode`
- **Linked Business Rules**: `INV-BR-003`

#### Scenarios & Acceptance Criteria
##### Scenario 1: Successful Lookup by Product Code
- **Given** Active inventory exists for product code `P001` with available quantity 50000.
- **When** `GET /api/inventory/P001` is invoked.
- **Then** Return HTTP 200 with JSON body `id` (as string), `productCode: "P001"`, `availableQuantity: 50000`, `reservedItems: 5`.

##### Scenario 2: Lookup with Unbounded Delay Parameter
- **Given** Active inventory exists for `P001`.
- **When** `GET /api/inventory/P001?delay=2` is invoked.
- **Then** The thread sleeps for 2 seconds using `TimeUnit.SECONDS.sleep(2)` before returning HTTP 200. If interrupted, the thread interrupt flag is restored and lookup proceeds.

##### Scenario 3: Product Code Absent or Soft-Deleted
- **Given** Product code `UNKNOWN` does not exist or has `deleted = true`.
- **When** `GET /api/inventory/UNKNOWN` is invoked.
- **Then** Return HTTP 404 Not Found with an empty body.

---

### INV-FR-003: Bulk Inventory Retrieval by Product Codes

- **ID**: `INV-FR-003`
- **Category**: Functional Requirement
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Description**: Accepts a list of product codes via query parameter `codes` and returns a paginated `PagedResult<InventoryResponse>` matching only existing, active records. Missing or deleted product codes are silently omitted from the result.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/controllers/InventoryController.java#InventoryController#getInventoryByProductCodes`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/controllers/InventoryControllerIT.java#shouldFindInventoriesByProductCodes`
- **Linked Business Rules**: `INV-BR-003`

#### Scenarios & Acceptance Criteria
##### Scenario 1: Bulk Lookup of Existing and Missing Product Codes
- **Given** Active inventory records for `P001` and `P002`, but `P999` does not exist.
- **When** `GET /api/inventory/product?codes=P001&codes=P002&codes=P999` is invoked.
- **Then** Return HTTP 200 with `PagedResult` whose data contains records for `P001` and `P002`. `P999` is omitted, and `totalElements` reports 2.

---

### INV-FR-004: Inventory Creation and Deleted Record Restoration

- **ID**: `INV-FR-004`
- **Category**: Functional Requirement
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Description**: Creates a new inventory record with a TSID identifier. If a record with the same product code already exists in an active state, HTTP 409 Conflict is returned. If a record with the same product code exists but is soft-deleted (`deleted = true`), the record is **restored** (`deleted = false`) with the newly requested available quantity while preserving its original ID and reserved items.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryService.java#InventoryService#saveInventory`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/controllers/InventoryControllerIT.java#shouldRestoreDeletedInventoryOnCreate`
- **Linked Business Rules**: `INV-BR-001`, `INV-BR-004`

#### Scenarios & Acceptance Criteria
##### Scenario 1: Create New Unique Inventory Record
- **Given** No record exists for product code `P100`.
- **When** `POST /api/inventory` is invoked with `{"productCode": "P100", "availableQuantity": 100}`.
- **Then** Return HTTP 201 Created with TSID identifier, `availableQuantity: 100`, `reservedItems: 0`, and `deleted: false`.

##### Scenario 2: Conflict on Existing Active Product Code
- **Given** Active record already exists for `P001`.
- **When** `POST /api/inventory` is invoked with `{"productCode": "P001", "availableQuantity": 50}`.
- **Then** Return HTTP 409 Conflict with RFC 7807 ProblemDetail stating product already exists.

##### Scenario 3: Restore Soft-Deleted Product Code
- **Given** Record `id = 1`, `productCode = "P001"`, `reserved_items = 5`, `deleted = true`.
- **When** `POST /api/inventory` is invoked with `{"productCode": "P001", "availableQuantity": 200}`.
- **Then** Return HTTP 201 Created with same `id = 1`, `availableQuantity = 200`, `reservedItems = 5`, and `deleted = false`.

---

### INV-FR-005: Inventory Update by Identifier and Code

- **ID**: `INV-FR-005`
- **Category**: Functional Requirement
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Description**: Updates available quantity of active inventory by database ID or by product code. Reserved items are preserved. If updating by product code, the request body `productCode` must match the URL path variable if non-null; a mismatch returns empty HTTP 400 Bad Request.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/controllers/InventoryController.java#InventoryController#updateInventory`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/controllers/InventoryControllerIT.java#shouldUpdateInventory`
- **Linked Business Rules**: `INV-BR-002`, `INV-BR-003`
- **Linked Questions**: `INV-Q-003` (Null availableQuantity behavior)

#### Scenarios & Acceptance Criteria
##### Scenario 1: Update Existing Inventory by ID
- **Given** Active inventory `id = 1` with `quantity = 50000`, `reserved_items = 5`.
- **When** `PUT /api/inventory/1` is invoked with `{"productCode": "P001", "availableQuantity": 75000}`.
- **Then** Return HTTP 200 with updated `availableQuantity: 75000` and unchanged `reservedItems: 5`.

##### Scenario 2: Update by Product Code with Code Mismatch
- **Given** Route `/api/inventory/product/P001`.
- **When** `PUT /api/inventory/product/P001` is invoked with `{"productCode": "P002", "availableQuantity": 10}`.
- **Then** Return HTTP 400 Bad Request with empty body.

##### Scenario 3: Update Non-Existing or Soft-Deleted Record
- **Given** ID `999` does not exist or is soft-deleted.
- **When** `PUT /api/inventory/999` is invoked.
- **Then** Return HTTP 404 Not Found with empty body.

---

### INV-FR-006: Soft Deletion of Inventory Records

- **ID**: `INV-FR-006`
- **Category**: Functional Requirement
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Description**: Marks active inventory as soft-deleted by setting `deleted = true`. Available and reserved quantities are left unchanged. If the ID exists and is active, returns HTTP 200. If the ID is absent or already soft-deleted, returns HTTP 410 Gone.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/controllers/InventoryController.java#InventoryController#deleteInventory`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/controllers/InventoryControllerIT.java#shouldDeleteInventory`
- **Linked Business Rules**: `INV-BR-003`, `INV-BR-004`

#### Scenarios & Acceptance Criteria
##### Scenario 1: Successfully Soft-Delete Active Inventory
- **Given** Active inventory `id = 1`, `deleted = false`.
- **When** `DELETE /api/inventory/1` is invoked.
- **Then** Return HTTP 200 OK with empty body. In database, `deleted` becomes `true`.

##### Scenario 2: Delete Already Deleted or Missing Inventory
- **Given** Inventory `id = 1` already has `deleted = true` or `id = 999` does not exist.
- **When** `DELETE /api/inventory/1` or `DELETE /api/inventory/999` is invoked.
- **Then** Return HTTP 410 Gone with empty body.

---

### INV-FR-007: Batch Inventory Random Generation with Idempotency Key

- **ID**: `INV-FR-007`
- **Category**: Functional Requirement
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Description**: Generates random available stock (1 to 10,000) for product codes matching `ProductCode_<idempotencyKey>_<index>` across indexes `0` through `batchSize - 1`. The `Idempotency-Key` header is mandatory. Repeated keys are ignored for the service process lifetime using an in-memory set. Default `batchSize` is 101, valid range 1 to 10,000.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryGeneratorService.java#InventoryGeneratorService#updateGeneratedInventory`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/service/InventoryGeneratorServiceTest.java#testUpdateGeneratedInventory`
- **Linked Business Rules**: `INV-BR-005`

#### Scenarios & Acceptance Criteria
##### Scenario 1: Generate Batch with Valid Key and Default Batch Size
- **Given** Header `Idempotency-Key: "test-uuid"`.
- **When** `POST /api/inventory/generate` is called without `batchSize`.
- **Then** Returns HTTP 200 with boolean `true`. Iterates through 101 records (`ProductCode_test-uuid_0` to `ProductCode_test-uuid_100`), assigning random quantities between 1 and 10,000 to existing active records. Optimistic locking failures on individual items are swallowed.

##### Scenario 2: Re-use Same Idempotency Key in Same Instance
- **Given** Key `"test-uuid"` was previously processed.
- **When** `POST /api/inventory/generate` is called again with `Idempotency-Key: "test-uuid"`.
- **Then** Immediately returns HTTP 200 `true` without updating any records.

##### Scenario 3: Invalid Batch Size
- **When** `POST /api/inventory/generate?batchSize=0` or `batchSize=10001` is called.
- **Then** Throws `IllegalArgumentException`, resulting in HTTP 400, and does not claim the idempotency key.

---

### INV-FR-008: Event-Driven Order Reservation and Confirmation Handling

- **ID**: `INV-FR-008`
- **Category**: Functional Requirement
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Description**: Consumes order events from Kafka topic `orders`. For `status = "NEW"`, executes all-or-nothing stock reservation, stores outcome in `processed_order_events`, and publishes `InventoryProcessedEvent` (`OrderDto` payload) to `stock-orders` with `source = "INVENTORY"`. For other statuses (`CONFIRMED`, `ROLLBACK`), executes stock fulfillment or release via registered strategies.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/config/KafkaListenerConfig.java#KafkaListenerConfig#onEvent`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImplIT.java#reserveOrderWithSufficientStock_ShouldReturnAcceptStatus`
- **Linked Business Rules**: `INV-BR-006`, `INV-BR-007`, `INV-BR-008`

#### Scenarios & Acceptance Criteria
##### Scenario 1: Successful Order Reservation (Sufficient Stock)
- **Given** Order `orderId = 1`, `status = "NEW"`, requesting 2 units of `P001` (available: 10).
- **When** Kafka message arrives on topic `orders`.
- **Then** Deduplication claim `RESERVE` is recorded. Available stock decreases to 8, reserved increases by 2. Result status `ACCEPT` is saved. Event published to `stock-orders` with `status: "ACCEPT"`, `source: "INVENTORY"`, and claim `RESERVE_REPLY:<attemptId>` recorded.

##### Scenario 2: Insufficient Stock or Missing Product (All-or-Nothing Rejection)
- **Given** Order requesting 5 units of `P001` (available: 2) or requesting soft-deleted `P002`.
- **When** Kafka message arrives on topic `orders`.
- **Then** No stock quantities change. Claim records `result_status = "REJECT"`. Event published to `stock-orders` with `status: "REJECT"`, `source: "INVENTORY"`.

##### Scenario 3: Confirmation Event Decreases Reserved Stock
- **Given** Order with `status = "CONFIRMED"`, having 2 units reserved.
- **When** Kafka message arrives on topic `orders`.
- **Then** Claim `CONFIRM_CONFIRMED` is recorded. Reserved stock decreases by 2. Available stock is unchanged. No event published to `stock-orders`.

##### Scenario 4: Rollback Event Restores Available Stock
- **Given** Order with `status = "ROLLBACK"`, `source = "PAYMENT"`.
- **When** Kafka message arrives on topic `orders`.
- **Then** Claim `CONFIRM_ROLLBACK` is recorded. Reserved stock decreases by 2, available stock increases by 2.
- **Exception**: If `source` is case-insensitively equal to `"INVENTORY"`, rollback is a no-op (prevents self-rollback).

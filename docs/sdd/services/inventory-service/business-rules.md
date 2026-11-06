# Business Rules Specification: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`

This document specifies the domain invariants, validation rules, state constraints, and idempotency guarantees governing `inventory-service`.

---

## 1. Business Rules Index

| ID | Title | Enforcement Level | Evidence Status | Primary Source Reference |
| :--- | :--- | :--- | :--- | :--- |
| `INV-BR-001` | Unique Product Code Invariant | Database Unique Constraint | Verified | `01-create_inventories_table.json` |
| `INV-BR-002` | Stock Reservation & Balance Formula | Java Domain Service | Verified | `InventoryOrderManageServiceImpl#reserve` |
| `INV-BR-003` | Active Record Query Filtering | jOOQ & JPA Queries | Verified | `InventoryJOOQRepositoryImpl` |
| `INV-BR-004` | Soft Deletion State Semantics | JPA Lifecycle & Service | Verified | `InventoryService#saveInventory` |
| `INV-BR-005` | Generator In-Memory Idempotency | ConcurrentHashSet | Verified | `InventoryGeneratorService` |
| `INV-BR-006` | All-or-Nothing Order Reservation Invariant | Domain Service Loop | Verified | `InventoryOrderManageServiceImpl#reserve` |
| `INV-BR-007` | Deduplication via Composite Event Key | PostgreSQL ON CONFLICT | Verified | `ProcessedOrderEventRepository#claim` |
| `INV-BR-008` | Attempt-Scoped Reply Idempotency | Event Type Tracking | Verified | `InventoryOrderManageServiceImpl#reserve` |

---

## 2. Business Rule Specifications

### INV-BR-001: Unique Product Code Invariant

- **ID**: `INV-BR-001`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Statement**: Each product code must correspond to at most one record in the `inventory` table across the entire lifetime of the system, including soft-deleted records.
- **Enforcement Mechanism**: PostgreSQL database constraint `inventory_product_code_key` created in Liquibase migration `01-create_inventories_table.json`.
- **Source Reference**: `inventory-service/src/main/resources/db/changelog/migration/01-create_inventories_table.json#add_unique_constraint`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/controllers/InventoryControllerIT.java#shouldReturn409WhenCreateNewInventoryWithExistingProductCode`
- **Linked Requirements**: `INV-FR-004`
- **Context**: If a product code is soft-deleted, inserting the same code via JPA throws `DataIntegrityViolationException`, which is caught by `InventoryService#saveInventory` to restore the deleted record instead of creating a second row.

---

### INV-BR-002: Stock Reservation & Balance Formula

- **ID**: `INV-BR-002`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Statement**: Stock changes must obey exact balanced arithmetic across reservation, confirmation, and rollback:
  1. **Reservation (`ACCEPT`)**:
     $$\text{availableQuantity}_{\text{new}} = \text{availableQuantity}_{\text{prev}} - \text{requestedQuantity}$$
     $$\text{reservedItems}_{\text{new}} = \text{reservedItems}_{\text{prev}} + \text{requestedQuantity}$$
  2. **Confirmation (`CONFIRMED`)**:
     $$\text{reservedItems}_{\text{new}} = \text{reservedItems}_{\text{prev}} - \text{orderedQuantity}$$
     ($\text{availableQuantity}$ remains unchanged)
  3. **Rollback (`ROLLBACK`)**:
     $$\text{reservedItems}_{\text{new}} = \text{reservedItems}_{\text{prev}} - \text{orderedQuantity}$$
     $$\text{availableQuantity}_{\text{new}} = \text{availableQuantity}_{\text{prev}} + \text{orderedQuantity}$$
- **Enforcement Mechanism**: `InventoryOrderManageServiceImpl#reserve`, `ConfirmedOrderStockActionStrategy`, and `RollbackOrderStockActionStrategy`.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#reserve`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImplIT.java#assertInventoryChanges`
- **Linked Requirements**: `INV-FR-008`
- **Defect Note**: Notice that the database does not have a `CHECK (quantity >= 0)` constraint. If multiple non-atomic updates bypass JPA versioning or if rollback runs unexpectedly, negative stock is technically possible at the DB level, though Java checks prevent it for standard reservations.

---

### INV-BR-003: Active Record Query Filtering

- **ID**: `INV-BR-003`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Statement**: All client-facing read endpoints (`getAllInventories`, `getInventoryByProductCode`, `getInventoryByProductCodes`) and new order reservation lookups must filter exclusively for active records (`deleted = false`).
- **Enforcement Mechanism**: Explicit SQL/jOOQ predicates: `where(INVENTORY.DELETED.isFalse())` and JPA query methods: `findByProductCodeAndDeletedFalse`.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryJOOQRepositoryImpl.java#InventoryJOOQRepositoryImpl#findAllAndDeletedFalse`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/controllers/InventoryControllerIT.java#shouldFetchAllInventories`
- **Linked Requirements**: `INV-FR-001`, `INV-FR-002`, `INV-FR-003`, `INV-FR-008`

---

### INV-BR-004: Soft Deletion State Semantics

- **ID**: `INV-BR-004`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Statement**: Soft-deleting an inventory record sets `deleted = true` without modifying existing available or reserved quantities. Deleting an already deleted record is an idempotent no-op returning HTTP 410 Gone. Confirmation and rollback actions on existing reservations apply even if the underlying product has been soft-deleted.
- **Enforcement Mechanism**: `InventoryRepository#findByProductCodeIn` (unfiltered) in `InventoryOrderManageServiceImpl#confirm`.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#confirm`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImplIT.java#confirmAndRollbackForSoftDeletedProduct_ShouldComplete`
- **Linked Requirements**: `INV-FR-006`, `INV-FR-008`

---

### INV-BR-005: Generator In-Memory Idempotency

- **ID**: `INV-BR-005`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Statement**: Inventory batch random generation claims an idempotency key before visiting records. Once claimed, any subsequent request with the same `Idempotency-Key` is ignored for the lifetime of that application process. If a non-optimistic runtime exception occurs, the key is evicted from the set to allow retries.
- **Enforcement Mechanism**: `java.util.concurrent.ConcurrentHashMap.newKeySet()` in `InventoryGeneratorService`.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryGeneratorService.java#InventoryGeneratorService#updateGeneratedInventory`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/service/InventoryGeneratorServiceTest.java#shouldAcceptBatchSizeBoundariesAndIgnoreRepeatedKeys`
- **Linked Requirements**: `INV-FR-007`
- **Limitation Note**: Keys are stored in in-memory JVM heaps; restarting the pod or running multi-pod clusters resets or splits the idempotency scope.

---

### INV-BR-006: All-or-Nothing Order Reservation Invariant

- **ID**: `INV-BR-006`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Statement**: When reserving stock for an order containing multiple line items, the reservation succeeds if and only if **every** requested product is present, active (`deleted = false`), not duplicated in the item list, and has sufficient available quantity ($availableQuantity \ge requestedQuantity$). If any item fails this check, zero stock is modified, the reservation is rejected (`REJECT`), and counter `inventory_failures` is incremented.
- **Enforcement Mechanism**: Pre-validation loop and map population in `InventoryOrderManageServiceImpl#reserve`.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#reserve`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImplIT.java#reserveOrderWithInsufficientStock_ShouldReturnRejectStatus`
- **Linked Requirements**: `INV-FR-008`

---

### INV-BR-007: Deduplication via Composite Event Key

- **ID**: `INV-BR-007`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Statement**: Concurrent or re-delivered order messages with non-null `orderId` must execute state changes at most once per event type (`RESERVE` or `CONFIRM_<status>`). Claiming an event must be performed atomically via native database upsert. Orders with null `orderId` bypass deduplication tracking.
- **Enforcement Mechanism**: Atomic PostgreSQL SQL statement:
  ```sql
  INSERT INTO processed_order_events (order_id, event_type, processed_at)
  VALUES (:orderId, :eventType, :processedAt)
  ON CONFLICT (order_id, event_type) DO NOTHING
  ```
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/ProcessedOrderEventRepository.java#ProcessedOrderEventRepository#claim`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImplIT.java#concurrentTwoThreadDuplicateNewOrder_OnlyOneReservationAndOneReserveMarker`
- **Linked Requirements**: `INV-FR-008`

---

### INV-BR-008: Attempt-Scoped Reply Idempotency

- **ID**: `INV-BR-008`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Approved
- **Evidence Status**: Verified
- **Statement**: When a duplicate `RESERVE` order redelivery is detected for an already-processed order ID, no stock changes are performed. If the redelivered message contains a non-blank `attemptId` that has not yet been claimed, the service queries the stored `result_status` from `processed_order_events`, claims `RESERVE_REPLY:<attemptId>`, and republishes the stored outcome to `stock-orders` using the redelivered attempt ID. If the attempt has already been claimed or has a null/blank attempt ID, no event is republished.
- **Enforcement Mechanism**: `ProcessedOrderEventRepository#claim` with `eventType = "RESERVE_REPLY:" + attemptId`.
- **Source Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#reserve`
- **Test Reference**: `inventory-service/src/test/java/com/example/inventoryservice/inventory/web/service/InventoryOrderManageServiceImplIT.java#reserveOrder_AttemptsAAndB_ReservesStockOnceAndPublishesOneReplyPerAttempt`
- **Linked Requirements**: `INV-FR-008`

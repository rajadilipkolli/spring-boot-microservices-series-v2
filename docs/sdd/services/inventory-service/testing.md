# Testing and Verification Specification: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`

This document details the complete test suite inventory, Docker/Testcontainers requirements, test commands, and mapping of candidate test cases to requirements.

---

## 1. Testing Infrastructure & Verification

- **Build Tool**: Maven Wrapper (`mvnw.cmd` on Windows, `./mvnw` on Linux/macOS).
- **Execution Command**:
  ```bash
  ./mvnw -pl inventory-service verify
  ```
- **Testcontainers Prerequisites**:
  - Docker daemon must be running.
  - Automatically spins up Testcontainers for:
    - PostgreSQL (`PostgreSQLContainer`)
    - Apache Kafka (`KafkaContainer`)
- **Assertion Standards**: AssertJ (`assertThat(...)`).

---

## 2. Test Suite Inventory

The table below catalogs every test class in `inventory-service` with scope and exact test methods:

| Test Class | Category / Scope | Exact Test Methods | Notes / Misleading Names |
| :--- | :--- | :--- | :--- |
| `ModularityTests` | Architecture (Modulith) | `verifiesModularStructure`, `createModuleDocumentation` | Verifies module boundaries and generates docs. |
| `InventoryServiceApplicationIntegrationTest` | Context Smoke | `contextLoads` | Verifies Spring application context loads cleanly. |
| `SwaggerConfigIntegrationTest` | API Doc Integration | `shouldServeOpenAPIEndpoint` | Asserts `/v3/api-docs` returns HTTP 200. |
| `ConsumerProtocolIntegrationTest` | Kafka Protocol Integration | `verifiesConsumerProtocolGroupType` | Verifies Kafka consumer group protocol is set to consumer. |
| `InventoryControllerTest` | Controller MockMvc Unit | `shouldFetchAllInventories`, `shouldFindInventoryByProductCode`, `shouldReturn404WhenFetchingNonExistingInventory`, `shouldCreateNewInventory`, `shouldReturn400WhenCreateNewInventoryWithoutProductCode`, `shouldReturn400WhenCreateNewInventoryWithNegativeQuantity`, `shouldReturn404WhenUpdatingNonExistingInventory`, `shouldUpdateInventorySuccessfully`, `shouldReturn400WhenUpdatingWithInvalidJson`, `shouldDeleteInventory`, `shouldReturn410WhenDeletingNonExistingInventory`, `testUpdateInventoryWithRandomValue`, `shouldUpdateInventoryWithBatchSize`, `shouldRejectInvalidGenerationBatchSize`, `shouldReturn500WhenGenerationFails` | Unit tests for HTTP routes mocking `InventoryAPI`. |
| `InventoryControllerIT` | Controller Integration (Testcontainers) | `shouldFetchAllInventories`, `shouldFindInventoryByProductCode`, `shouldFindInventoriesByProductCodes`, `shouldCreateNewInventory`, `shouldRestoreDeletedInventoryOnCreate`, `shouldReturn400WhenCreateNewInventoryWithoutProductCode`, `shouldReturn409WhenCreateNewInventoryWithExistingProductCode`, `shouldUpdateInventory`, `shouldDeleteInventory`, `shouldUpdateInventoryByProductCode`, `shouldReturn404WhenUpdatingInventoryByNonExistingProductCode` | Full HTTP integration tests with PostgreSQL. |
| `InventoryGeneratorServiceTest` | Service Unit Test | `testUpdateGeneratedInventory`, `shouldUpdateRequestedGeneratedInventoryBatchSize`, `shouldRejectInvalidBatchSizeWithoutConsumingIdempotencyKey`, `shouldAcceptBatchSizeBoundariesAndIgnoreRepeatedKeys`, `shouldRemoveIdempotencyKeyOnNonOptimisticRuntimeExceptionToAllowRetry` | Tests in-memory idempotency set and random generator loop. |
| `InventoryOrderManageServiceImplTest` | Service Unit Test (Mocked) | `reserve_AllProductsExist_OrderStatusIsNew_OrderIsAccepted`, `reserve_AllProductsExistWithLessQuantity_OrderStatusIsNew_OrderIsRejected`, `reserve_NotAllProductsExist_OrderStatusIsNew_OrderIsRejectedAndKafkaMessageSent`, `reserve_OrderStatusIsNotNew_OrderIsIgnored`, `confirm`, `confirmWhenOrderStatusIsROLLBACK`, `shouldSkipDuplicateReserveEvent`, `reserve_DuplicateReserveWithBlankAttempt_ShouldSuppress`, `reserve_DuplicateReserveWithSameAttempt_ShouldSuppress`, `reserve_DuplicateReserveWithNewAttempt_ShouldReplayWithoutStockChanges`, `reserve_DuplicateReserveWithNewAttempt_OutcomeNull_ShouldNotReplay` | Verifies reservation business logic, replay logic, and mock events. |
| `InventoryOrderManageServiceImplIT` | Service Integration (Testcontainers) | `confirmOrderWithConfirmedStatus_ShouldDecreaseReservedItems`, `confirmOrderWithRollbackStatus_ShouldDecreaseReservedItemsAndIncreaseAvailableQuantity`, `confirmOrderWithRollbackStatusAndInventorySource_ShouldNotChangeInventory`, `reserveOrderWithSufficientStock_ShouldReturnAcceptStatus`, `reserveOrderWithInsufficientStock_ShouldReturnRejectStatus`, `parameterizedReserveOrderTest`, `reserveOrderWithDuplicateEvent_ShouldSkipProcessing`, `confirmOrderWithDuplicateExternalRollback_ShouldRestoreStockOnlyOnce`, `rejectedNewOrderRedelivery_KeepsStockUnchangedAndStoresReject`, `reserveOrder_AttemptsAAndB_ReservesStockOnceAndPublishesOneReplyPerAttempt`, `concurrentTwoThreadDuplicateNewOrder_OnlyOneReservationAndOneReserveMarker`, `failureAfterClaim_RollsBackMarkerAndAllowsSuccessfulLaterDelivery`, `confirmOrderWithDuplicateEvent_ShouldSkipProcessing`, `reserveOrderForSoftDeletedProduct_ShouldRejectReservation`, `confirmAndRollbackForSoftDeletedProduct_ShouldComplete` | Exhaustive integration suite testing saga concurrency and rollbacks. |
| `InventoryRepositoryTest` | Repository DataJpaTest | `shouldDeleteByProductCodeWithoutAnExistingTransaction` | Verifies standalone physical delete. |
| `InventoryServiceTest` | Service Unit Test | `testSaveInventory`, `testSaveInventory_Conflict` | Unit tests for `saveInventory`. |
| `JOOQInventoryRepositoryTest` | Repository Integration | `findByProductCodeInAndQuantityAvailable` | Verifies jOOQ bulk queries. |
| `KafkaListenerConfigIntTest` | Kafka Listener Integration | `onNewOrderEvent`, `onSaveProductEvent`, `onDeleteProductEvent`, `onProductEventWithUnknownFields`, `onDuplicateNewOrderEvent_DecreasesAvailableQuantityOnlyOnce`, `onDuplicateProductEvent_CreatesOnlyOneProductRow` | Full async Kafka integration test with embedded Kafka container. |
| `ProductManageServiceImplTest` | Service Unit Test | `manage`, `manageWhenDeletedAndActiveShouldSoftDelete`, `manageWhenDeletedAndAlreadyDeletedShouldDoNothing`, `manageWhenDeletedAndMissingShouldDoNothing`, `manageWhenNotDeletedAndSoftDeletedExistsShouldNotRecreate`, `manageWhenSoftDeletedInventoryAppearsDuringSaveShouldIgnoreConflict`, `manageWhenIntegrityFailureLeavesProductCodeAbsentShouldPropagate` | Comprehensive unit tests for product sync cases. |

---

## 3. Discrepancies and Misleading Test Names

1. **`JOOQInventoryRepositoryTest#findByProductCodeInAndQuantityAvailable`**:
   The test executes direct DSL SQL with `PRODUCT_CODE.in(...)` and `QUANTITY.gt(0)`, asserting that only the positive-quantity record is returned. It does not invoke `InventoryJOOQRepositoryImpl`.
2. **`InventoryRepositoryTest#shouldDeleteByProductCodeWithoutAnExistingTransaction`**:
   Verifies physical database deletion (`deleteByProductCode`), which is only used in test cleanup routines and is not exposed in public REST controllers.

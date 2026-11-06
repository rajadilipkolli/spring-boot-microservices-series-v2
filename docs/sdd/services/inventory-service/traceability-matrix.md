# Requirements Traceability Matrix: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`

This matrix provides comprehensive end-to-end traceability mapping every Functional Requirement (`INV-FR`), Business Rule (`INV-BR`), and Non-Functional Requirement (`INV-NFR`) to its specification section, concrete production source symbol, exact automated test reference, and evidence status.

---

## 1. Traceability Table

| Requirement ID | Specification Section | Source Symbol | Test Reference | Evidence Status |
| :--- | :--- | :--- | :--- | :--- |
| **`INV-FR-001`** | [Functional Requirements](functional-requirements.md) | `inventory-service/.../InventoryController.java#InventoryController#getAllInventories` | `inventory-service/.../InventoryControllerIT.java#shouldFetchAllInventories` | Verified |
| **`INV-FR-002`** | [Functional Requirements](functional-requirements.md) | `inventory-service/.../InventoryController.java#InventoryController#getInventoryByProductCode` | `inventory-service/.../InventoryControllerIT.java#shouldFindInventoryByProductCode` | Verified |
| **`INV-FR-003`** | [Functional Requirements](functional-requirements.md) | `inventory-service/.../InventoryController.java#InventoryController#getInventoryByProductCodes` | `inventory-service/.../InventoryControllerIT.java#shouldFindInventoriesByProductCodes` | Verified |
| **`INV-FR-004`** | [Functional Requirements](functional-requirements.md) | `inventory-service/.../InventoryService.java#InventoryService#saveInventory` | `inventory-service/.../InventoryControllerIT.java#shouldRestoreDeletedInventoryOnCreate` | Verified |
| **`INV-FR-005`** | [Functional Requirements](functional-requirements.md) | `inventory-service/.../InventoryController.java#InventoryController#updateInventory` | `inventory-service/.../InventoryControllerIT.java#shouldUpdateInventory` | Verified |
| **`INV-FR-006`** | [Functional Requirements](functional-requirements.md) | `inventory-service/.../InventoryController.java#InventoryController#deleteInventory` | `inventory-service/.../InventoryControllerIT.java#shouldDeleteInventory` | Verified |
| **`INV-FR-007`** | [Functional Requirements](functional-requirements.md) | `inventory-service/.../InventoryGeneratorService.java#InventoryGeneratorService#updateGeneratedInventory` | `inventory-service/.../InventoryGeneratorServiceTest.java#testUpdateGeneratedInventory` | Verified |
| **`INV-FR-008`** | [Functional Requirements](functional-requirements.md) | `inventory-service/.../InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#reserve` | `inventory-service/.../InventoryOrderManageServiceImplIT.java#reserveOrderWithSufficientStock_ShouldReturnAcceptStatus` | Verified |
| **`INV-BR-001`** | [Business Rules](business-rules.md) | `inventory-service/.../01-create_inventories_table.json#add_unique_constraint` | `inventory-service/.../InventoryControllerIT.java#shouldReturn409WhenCreateNewInventoryWithExistingProductCode` | Verified |
| **`INV-BR-002`** | [Business Rules](business-rules.md) | `inventory-service/.../InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#reserve` | `inventory-service/.../InventoryOrderManageServiceImplIT.java#assertInventoryChanges` | Verified |
| **`INV-BR-003`** | [Business Rules](business-rules.md) | `inventory-service/.../InventoryJOOQRepositoryImpl.java#InventoryJOOQRepositoryImpl#findAllAndDeletedFalse` | `inventory-service/.../InventoryControllerIT.java#shouldFetchAllInventories` | Verified |
| **`INV-BR-004`** | [Business Rules](business-rules.md) | `inventory-service/.../InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#confirm` | `inventory-service/.../InventoryOrderManageServiceImplIT.java#confirmAndRollbackForSoftDeletedProduct_ShouldComplete` | Verified |
| **`INV-BR-005`** | [Business Rules](business-rules.md) | `inventory-service/.../InventoryGeneratorService.java#InventoryGeneratorService` | `inventory-service/.../InventoryGeneratorServiceTest.java#shouldAcceptBatchSizeBoundariesAndIgnoreRepeatedKeys` | Verified |
| **`INV-BR-006`** | [Business Rules](business-rules.md) | `inventory-service/.../InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#reserve` | `inventory-service/.../InventoryOrderManageServiceImplIT.java#reserveOrderWithInsufficientStock_ShouldReturnRejectStatus` | Verified |
| **`INV-BR-007`** | [Business Rules](business-rules.md) | `inventory-service/.../ProcessedOrderEventRepository.java#ProcessedOrderEventRepository#claim` | `inventory-service/.../InventoryOrderManageServiceImplIT.java#concurrentTwoThreadDuplicateNewOrder_OnlyOneReservationAndOneReserveMarker` | Verified |
| **`INV-BR-008`** | [Business Rules](business-rules.md) | `inventory-service/.../InventoryOrderManageServiceImpl.java#InventoryOrderManageServiceImpl#reserve` | `inventory-service/.../InventoryOrderManageServiceImplIT.java#reserveOrder_AttemptsAAndB_ReservesStockOnceAndPublishesOneReplyPerAttempt` | Verified |
| **`INV-NFR-001`** | [Non-Functional Requirements](non-functional-requirements.md) | `docs/runbooks/SLO-Definitions.md#SLO-Definitions` | None | Target |
| **`INV-NFR-002`** | [Non-Functional Requirements](non-functional-requirements.md) | `docs/runbooks/SLO-Definitions.md#SLO-Definitions` | None | Target |
| **`INV-NFR-003`** | [Non-Functional Requirements](non-functional-requirements.md) | `docs/runbooks/SLO-Definitions.md#SLO-Definitions` | None | Target |
| **`INV-NFR-004`** | [Non-Functional Requirements](non-functional-requirements.md) | `deployment/k8s/base/pdb.yaml#inventory-service-pdb` | None | Configured |
| **`INV-NFR-005`** | [Non-Functional Requirements](non-functional-requirements.md) | `deployment/k8s/overlays/autoscaling/keda.yaml#inventory-service-scaler` | None | Configured |
| **`INV-NFR-006`** | [Non-Functional Requirements](non-functional-requirements.md) | `deployment/k8s/base/inventory-service.yaml#inventory-service` | None | Configured |
| **`INV-NFR-007`** | [Non-Functional Requirements](non-functional-requirements.md) | `deployment/k8s/base/network-policies.yaml#allow-apps-to-apps` | None | Configured |
| **`INV-NFR-008`** | [Non-Functional Requirements](non-functional-requirements.md) | `inventory-service/pom.xml` | None | Needs-Clarification |

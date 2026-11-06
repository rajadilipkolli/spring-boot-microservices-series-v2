# Open Questions and Proposed Changes: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Living Document`

This document captures open ambiguities, suspected defects, stale documentation discrepancies, and proposed future improvements. In accordance with SDD rules, suspected defects are documented as current behavior with evidence statuses and linked questions (`INV-Q`), while all bug fixes and improvements remain in unapproved proposed changes (`INV-PROP`).

---

## 1. Open Questions (`INV-Q-<NNN>`)

### INV-Q-001: Discrepancy in Kafka Retry and Dead-Letter Topic Naming
- **Category**: Stale Documentation Discrepancy
- **Status**: Open
- **Date Identified**: 2026-11-05
- **Context**: In `inventory-service/README.md` and `inventory-service/InventoryService-Sequence-Diagram.md`, retry topics are documented as `orders-retry-0,1,2` and DLT is documented as `orders-dlt`. However, `KafkaListenerConfig.java` declares explicit suffixing:
  ```java
  retryTopicSuffix = "-retry-inventory",
  dltTopicSuffix = "-dlt-inventory"
  ```
  The runtime topics are therefore `orders-retry-inventory-0`, etc.
- **Evidence**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/config/KafkaListenerConfig.java#KafkaListenerConfig#onEvent`.
- **Linked Proposal**: `INV-PROP-001`.

---

### INV-Q-002: Inferred 500 Internal Server Error on Invalid Sort Field
- **Category**: Inferred Behavior / Untested Status
- **Status**: Open
- **Date Identified**: 2026-11-05
- **Context**: In `InventoryJOOQRepositoryImpl#getTableField`, reflection is used to look up table fields by sort property. If an invalid or unmapped property is passed in `sortBy`, it catches `NoSuchFieldException` and throws `InvalidDataAccessApiUsageException`. This exception is not caught by `GlobalExceptionHandler` and falls through to the generic `handleGenericException`, returning HTTP 500 instead of HTTP 400 Bad Request. There are no automated tests asserting this error condition.
- **Evidence**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryJOOQRepositoryImpl.java#InventoryJOOQRepositoryImpl#getTableField`.
- **Linked Proposal**: `INV-PROP-002`.

---

### INV-Q-003: Product Code Renaming via PUT Identifier and Null Quantity Behavior
- **Category**: Suspected Defect
- **Status**: Open
- **Date Identified**: 2026-11-05
- **Context**:
  1. In `InventoryController#updateInventory(@PathVariable Long id, @RequestBody InventoryRequest request)`, the method passes the request to `inventoryMapper.updateInventoryFromRequest(inventoryRequest, inventory)`. In `Inventory.java`, `product_code` is marked `@Column(updatable = false)`. If a caller supplies a different `productCode` in the body, JPA silently ignores the change during SQL generation, but in-memory DTO mapping might reflect the requested code.
  2. If `availableQuantity` is `null` in `InventoryRequest`, MapStruct overwrites the existing quantity with `null`. In PostgreSQL, `quantity` is nullable, which can cause stock calculations to fail or encounter NPEs during subsequent reservation queries.
- **Evidence**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/web/service/InventoryService.java#InventoryService#updateInventory`.
- **Linked Proposal**: `INV-PROP-003`.

---

### INV-Q-004: Inactive Resilience4j Properties in Config Server
- **Category**: Stale Configuration / Architecture Gap
- **Status**: Open
- **Date Identified**: 2026-11-05
- **Context**: In `config-server/.../config-repository/inventory-service.properties`, complete configurations for `resilience4j.retry`, `resilience4j.ratelimiter`, and `resilience4j.circuitbreaker` are provided. However, `inventory-service/pom.xml` does not include `resilience4j-spring-boot3` or similar dependencies. The properties are unused.
- **Evidence**: `config-server/src/main/resources/config-repository/inventory-service.properties` and `inventory-service/pom.xml`.
- **Linked Proposal**: `INV-PROP-004`.

---

### INV-Q-005: Absence of Service-Level Authentication and Authorization
- **Category**: Security Clarification
- **Status**: Open
- **Date Identified**: 2026-11-05
- **Context**: `inventory-service` has no Spring Security dependency or token validation filter. Any pod in the cluster that can route to port 18181 can call mutating endpoints (`POST`, `PUT`, `DELETE`) without authentication. Security relies exclusively on Kubernetes network policies and perimeter gateway authentication.
- **Evidence**: `inventory-service/pom.xml` and `deployment/k8s/base/network-policies.yaml`.
- **Linked Proposal**: `INV-PROP-005`.

---

### INV-Q-006: Missing Apicurio Schema Registry Integration (ADR-010)
- **Category**: Architecture Decision Alignment
- **Status**: Open
- **Date Identified**: 2026-11-05
- **Context**: [ADR-010: Schema Governance Apicurio Registry](../../../../docs/adr/ADR-010-Schema-Governance-Apicurio-Registry.md) mandates Apicurio Schema Registry for Kafka event governance in production. Currently, `inventory-service` uses plain JSON serialization (`ByteArrayJacksonJsonMessageConverter`) without Schema Registry integration or Avro/JSON Schema registration.
- **Evidence**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/config/EventExternalizationConfig.java`.
- **Linked Proposal**: `INV-PROP-006`.

---

### INV-Q-007: Lack of Retention / Expiry Policy on Processed Order Events and Outbox Archive
- **Category**: Operational Growth / Database Invariant
- **Status**: Open
- **Date Identified**: 2026-11-05
- **Context**: Tables `processed_order_events` and `event_publication_archive` grow monotonically with every order processed. There are no Liquibase partitioning schemes, TTL indexes, or background purge jobs to clean up completed events, which will eventually impact storage and query performance over time.
- **Evidence**: `inventory-service/src/main/resources/db/changelog/migration/05-create-processed-order-events.sql` and `04-create-event_publication_archive.sql`.
- **Linked Proposal**: `INV-PROP-007`.

---

## 2. Proposed Changes (`INV-PROP-<NNN>`)

> [!NOTE]
> All proposed changes below are unapproved suggestions. No code changes have been applied.

### INV-PROP-001: Align Legacy README and Diagram with Actual Topic Suffixes
- **Status**: `Draft` (Unapproved)
- **Motivating Question**: `INV-Q-001`
- **Contract Impact**: `[Compatible]` (Documentation update only)
- **Proposed Behavior**: Update `inventory-service/README.md` and `inventory-service/InventoryService-Sequence-Diagram.md` to reference `-retry-inventory` and `-dlt-inventory` topic suffixes.

---

### INV-PROP-002: Translate Invalid Paging/Sorting Arguments to HTTP 400
- **Status**: `Draft` (Unapproved)
- **Motivating Question**: `INV-Q-002`
- **Contract Impact**: `[Compatible]`
- **Proposed Behavior**: Add an `@ExceptionHandler(InvalidDataAccessApiUsageException.class)` in `GlobalExceptionHandler` to translate unrecognized sort field names into an RFC 7807 `ProblemDetail` with status `400 Bad Request`. Add a unit test in `InventoryControllerTest`.

---

### INV-PROP-003: Disallow Product Code Mutation and Enforce Non-Null Quantity on PUT
- **Status**: `Draft` (Unapproved)
- **Motivating Question**: `INV-Q-003`
- **Contract Impact**: `[Compatible]`
- **Proposed Behavior**:
  1. Add `@NotNull` on `availableQuantity` in `InventoryRequest`.
  2. In `InventoryMapper`, configure `productCode` as ignored during updates (`@Mapping(target = "productCode", ignore = true)`).
  3. Reject `PUT /api/inventory/{id}` with 400 if the body contains a conflicting non-null `productCode`.

---

### INV-PROP-004: Clean Up Unused Resilience4j Properties in Config Repository
- **Status**: `Draft` (Unapproved)
- **Motivating Question**: `INV-Q-004`
- **Contract Impact**: `[Compatible]`
- **Proposed Behavior**: Either add `resilience4j-spring-boot3` starter to `inventory-service/pom.xml` to enable circuit breaking, or remove unused `resilience4j.*` properties from `config-repository/inventory-service.properties`.

---

### INV-PROP-005: Introduce Spring Security OAuth2 Resource Server
- **Status**: `Draft` (Unapproved)
- **Motivating Question**: `INV-Q-005`
- **Contract Impact**: `[Breaking]`
- **Proposed Behavior**: Add `spring-boot-starter-oauth2-resource-server` and enforce JWT bearer token validation on internal microservice HTTP endpoints.

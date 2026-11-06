# Inventory Service (`inventory-service`) Pilot Specification Overview

- **Service Name**: `inventory-service`
- **Service Prefix**: `INV`
<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:039643b3bb10c26f591f1c3e4adee2a036714da478bb329660bc7dda3cd1c724 -->

- **Specification Version**: `1.1.2`

<!-- /coderabbit-ai-generated:v1 -->
- **Specification Status**: `Approved`
- **Owner / Team**: Commerce Platform Squad
- **Repository Location**: `inventory-service/`
- **Base Context Path**: `/inventory-service`
- **API Base Path**: `/inventory-service/api/inventory`
- **Default Port**: `18181`

---

## 1. Service Mission and Purpose

The **Inventory Service** manages product stock levels, inventory reservations for incoming orders, and event-driven stock synchronization across the platform. It provides REST APIs for querying and maintaining stock, listens for order events from Apache Kafka to execute two-phase reservations and confirmations/rollbacks, and synchronizes product catalogs asynchronously.

The service utilizes a hybrid persistence architecture combining **Spring Data JPA** for entity lifecycle management and optimistic locking with **jOOQ** for type-safe, high-performance query execution and pagination. Distributed messaging is coordinated via **Spring Modulith** with transactional outbox externalization and Kafka consumers configured with non-blocking retry topics.

### Key Ownership Boundaries
- **In-Scope / Owned**:
  - Authoritative inventory stock levels (`quantity` and `reserved_items`) for products.
  - Stock reservation and release during order checkout sagas.
  - Tracking processed order event claims and deduplication (`processed_order_events`).
  - Soft-deletion lifecycle of inventory records.
- **Out-of-Scope / Not Owned**:
  - Product metadata, pricing, descriptions, and catalog categories (owned exclusively by `catalog-service`).
  - Order state orchestration and payment authorization (owned by `order-service` and `payment-service`).
  - Edge routing and authentication enforcement (handled by `api-gateway`).

---

## 2. Specification Navigation Index

| Document | Topic | Description |
| :--- | :--- | :--- |
| [Domain Model](domain-model.md) | Domain Entities & DTOs | Ubiquitous glossary, `Inventory`, `ProcessedOrderEvent`, all six DTOs, and invariants. |
| [Functional Requirements](functional-requirements.md) | Requirements (`INV-FR-001`–`INV-FR-008`) | Granular capability specifications with Given/When/Then scenarios. |
| [Business Rules](business-rules.md) | Invariants (`INV-BR-001`–`INV-BR-008`) | Domain invariants, stock formulas, soft deletion, and idempotency rules. |
| [API Contracts](api-contracts.md) | Synchronous REST Endpoints | All eight HTTP routes, paging parameters, DTO schemas, and RFC 7807 error models. |
| [Data Model](data-model.md) | Persistence & Schema | 4 tables, migrations 01–07, rollbacks, and transaction boundaries. |
| [Workflows](workflows.md) | Workflows & Sagas | Reserve, confirm/rollback, product sync, and generator workflows. |
| [Integrations](integrations.md) | Messaging & Kafka | Topic bindings, Modulith outbox, retries (`-retry-inventory`), and DLT handling. |
| [Architecture](architecture.md) | Structural Architecture | Spring Modulith modules, component responsibilities, dependency stack. |
| [Non-Functional Requirements](non-functional-requirements.md) | Quality Attributes (`INV-NFR-001`–`INV-NFR-008`) | SLO targets, PDB configuration, security context, and observability. |
| [Operations](operations.md) | Runtime & Operations | Profiles, Config Server properties, Kubernetes deployment, probes, and runbooks. |
| [Testing](testing.md) | Testing & Verification | Unit, integration (Testcontainers), and architecture test inventory and coverage. |
| [Traceability Matrix](traceability-matrix.md) | Requirements Traceability | Matrix mapping every FR, BR, and NFR to source symbols and tests. |
| [Open Questions](open-questions.md) | Open Issues & Proposals | Discrepancies, suspected defects (`INV-Q`), and unapproved change proposals (`INV-PROP`). |

---

## 3. Change Log

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:b241517b5d233132b436b11125b61e7979271f55d2a19857131ddba4b58b1de1 -->

### [1.1.2] - 2026-11-06
- Moved duplicate-create classification after transaction completion while preserving deletion failure propagation.
- Clarified per-item generation transactions, direct repository save behavior, and configured inventory ingress peers.

### [1.1.1] - 2026-11-06

<!-- /coderabbit-ai-generated:v1 -->
- Corrected test references, paging, response and availability documentation. Network policy exceptions remain unverified.
- Wrapped complete write attempts in fresh transactions, including commit-time optimistic-lock handling.

### [1.1.0] - 2026-11-05
- **Architecture Refactoring**:
  - Re-architected `inventory-service` to strictly adhere to Spring Modulith conventions, SOLID principles, and CQRS read/write separation.
  - All read operations (single record queries, existence checks, paginated lookups, batch queries) are now routed exclusively through **jOOQ** (`InventoryJOOQRepository`).
  - All write operations (insert, update, delete) are routed through **Spring Data JPA** (`InventoryRepository`).
  - Introduced `InventoryRetryHandler` for custom fallback handling on concurrency conflicts / optimistic locking failures with retry and backoff.
  - Eliminated circular self-injection proxy in `InventoryService`.

### [1.0.0] - 2026-11-05
- **Baseline Release**: Reverse-engineered pilot specification established from production Java source code, Testcontainers integration test suites, Liquibase migrations (01–07), Config Server properties, and Kubernetes manifests.
- **Clarifications**: Resolved topic naming discrepancy between legacy README/diagram (`orders-retry-0,1,2`) and source code (`-retry-inventory`, `-dlt-inventory`) via `INV-Q-001`.

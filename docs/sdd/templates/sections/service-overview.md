# [Service Name] Specification Overview

- **Service Name**: `<service-name>`
- **Service Prefix**: `<SVC>`
- **Specification Version**: `1.0.0`
- **Specification Status**: `Approved` | `Draft`
- **Owner / Team**: [Owning Team or Squad]
- **Repository Location**: `<repo-relative-path-to-service>`
- **Base Context Path**: `/<service-name>`
- **API Base Path**: `/<service-name>/api/...`
- **Default Port**: `<service-port>`

---

## 1. Service Mission and Purpose *(Mandatory)*
[Provide a clear 2-3 paragraph explanation of the service's primary responsibility, what business capability it delivers to the platform, and its key architectural boundaries.]

---

## 2. Ownership Boundaries *(Mandatory)*

### 2.1 What This Service Owns
- [Domain entities, persistence stores, primary business logic owned exclusively by this service]
- [Authority over specific lifecycle states]

### 2.2 What This Service Does Not Own
- [Adjacent domain responsibilities delegated to other microservices]
- [Shared infrastructure or client-facing aggregation concerns]

---

## 3. Specification Document Index *(Mandatory)*

| Document | Topic | Description |
| :--- | :--- | :--- |
| [Domain Model](domain-model.md) | Domain Entities & DTOs | Ubiquitous glossary, aggregates, entities, value objects, and DTOs. |
| [Functional Requirements](functional-requirements.md) | Requirements (`<SVC>-FR-<NNN>`) | Detailed capability specifications with Given/When/Then scenarios. |
| [Business Rules](business-rules.md) | Invariants (`<SVC>-BR-<NNN>`) | Domain constraints, validation rules, and state invariants. |
| [API Contracts](api-contracts.md) | Synchronous REST Endpoints | HTTP routes, parameters, request/response bodies, and RFC 7807 errors. |
| [Data Model](data-model.md) | Persistence & Schema | Tables, columns, indexes, Liquibase migrations, and transactions. |
| [Workflows](workflows.md) | Business Workflows & Sagas | Multi-step workflows, saga orchestration/choreography, and state machines. |
| [Integrations](integrations.md) | Asynchronous Messaging | Kafka topics, event payloads, outbox configuration, retries, and DLT. |
| [Architecture](architecture.md) | Structural Architecture | Package structure, Spring Modulith boundaries, component diagrams. |
| [Non-Functional Requirements](non-functional-requirements.md) | NFRs (`<SVC>-NFR-<NNN>`) | Performance SLOs, scalability, high availability, security context. |
| [Operations](operations.md) | Runtime & Operations | Spring profiles, Config Server, Kubernetes deployment, probes, runbooks. |
| [Testing](testing.md) | Test Strategy & Verification | Test inventory, Testcontainers setup, verification commands. |
| [Traceability Matrix](traceability-matrix.md) | Requirements Traceability | End-to-end mapping from FR/BR/NFR to source code and automated tests. |
| [Open Questions](open-questions.md) | Discrepancies & Proposals | Open questions (`<SVC>-Q-<NNN>`) and proposed changes (`<SVC>-PROP-<NNN>`). |

---

## 4. Change Log *(Mandatory)*

### [1.0.0] - YYYY-MM-DD
- **Initial Baseline**: Reverse-engineered service specification established from source code, tests, and configuration.

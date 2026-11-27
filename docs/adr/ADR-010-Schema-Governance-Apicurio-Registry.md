# ADR-010: Schema Governance and Compatibility via Apicurio Registry

## Status
Accepted

## Context
In an event-driven architecture where microservices publish and consume asynchronous messages via Kafka (`orders`, `payment-orders`, `stock-orders`), changes to message structures can silently break consumers if schemas evolve incompatibly.

A producer updating an event payload by deleting a field or altering a data type without schema governance causes downstream consumer deserialization crashes, creating "poison pills" that block Kafka topic partitions.

## Decision
We establish a **Centralized Schema Governance Framework** using **Apicurio Registry**:

1. **Schema Format Standardization**:
   - Event contracts for inter-service messaging are defined using **Apache Avro** or **JSON Schema** specifications stored in `common-dtos` or schema definitions directories.
   - Schemas clearly specify required fields, optional fields with defaults, docstrings, and namespace mappings.

2. **Registry Integration & Compatibility Modes**:
   - **Apicurio Registry** runs in the cluster as the central artifact repository for schemas.
   - **Compatibility Policy**: Enforces **`BACKWARD`** or **`FULL`** compatibility for all production event schemas:
     - `BACKWARD`: Consumers using schema version $N$ can process events produced with schema version $N+1$. Fields may be added only if they define a default value; fields cannot be deleted without deprecation cycles.
     - `FULL`: Old consumers can read events from new producers, and new consumers can read events from old producers.

3. **Build-Time Verification**:
   - Schema validation is integrated into CI pipelines via the Apicurio Registry Maven Plugin (`apicurio-registry-maven-plugin`).
   - Every pull request that modifies an event DTO executes schema compatibility checks against the registered schema artifact in Apicurio before code can be merged.

4. **Runtime Deserialization Protection**:
   - Consumer microservices configure `ErrorHandlingDeserializer` delegates wrapping their Kafka deserializers.
   - Deserialization failures do not crash the consumer loop; instead, malformed records are trapped, logged with stack traces, and routed directly to dedicated Dead Letter Queues (`orders-dlt-payment`, `orders-dlt-inventory`) for triage.

## Consequences

### Positive
- **Guaranteed Compatibility**: No producer can deploy an event change that will crash existing running consumers.
- **Contract-First Development**: Event contracts are versioned, documented, and discoverable in a single registry UI/API.
- **Elimination of Poison Pills**: Deserialization error handling combined with build-time schema checks prevents partition blockages.

### Negative / Trade-offs
- Deployment Dependency: Apicurio Registry must be accessible to CI pipelines and microservices during schema registration.
- Developer Discipline: Developers must write explicit default values when adding new event fields.

## Verification & Compliance
- CI pipelines must execute `mvn apicurio-registry:test-update` on pull requests modifying shared DTO contracts.
- DLQ runbooks (`docs/runbooks/DLQ-Handling.md`) govern investigation procedures if incompatible payloads trigger DLT alerts.

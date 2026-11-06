# Change Management Guidelines

This document governs the procedures for updating specifications, managing versions, handling deprecations, proposing architectural modifications, and maintaining contract backward compatibility.

---

## 1. Same-PR Specification Rule

> [!IMPORTANT]
> **Atomic Delivery**: Any Pull Request (PR) that alters application behavior, REST endpoints, DTO structures, database schemas, messaging topics, or operational configurations **MUST** update the corresponding service specification under `docs/sdd/services/<service>/` within the **very same PR**.
> Pull requests that alter runtime behavior without updating the specification must fail code review.

---

## 2. Service Specification Versioning and Change Log

Each service's `docs/sdd/services/<service>/README.md` maintains a formal specification version following Semantic Versioning (`MAJOR.MINOR.PATCH`):

1. **`MAJOR`**: Breaking contract changes (e.g., removing a REST endpoint, altering required JSON request fields, changing Kafka event payload structure in an incompatible manner, or dropping a database column).
2. **`MINOR`**: Backward-compatible functional additions (e.g., adding a new optional query parameter, introducing a new endpoint, adding an optional field to an event schema, or adding a new non-null database column with a default value).
3. **`PATCH`**: Backward-compatible bug fixes, documentation clarifications, test mapping additions, or non-behavioral adjustments.

### Change Log Format
Every update to the service specification must prepend an entry to the `Change Log` section in the service's `README.md`:

```markdown
## Change Log

### [1.1.0] - 2026-11-05
- **Changed**: Documented composite event key correlation with Order Service (`INV-FR-005`).
- **Added**: Added soft-deletion recovery specifications (`INV-FR-008`).
- **Fixed**: Corrected topic retry suffixes to `-retry-inventory` (`INV-Q-001`).
```

---

## 3. Approval and Deprecation Governance

### Approval Rules
- Changes to `Draft` requirements require peer review by domain owners or service maintainers before being marked `Approved`.
- For autonomous AI agents, existing behavior reverse-engineered from source and tests is marked `Approved` baseline upon completion of the pilot specification.
- New proposed features or bug fixes begin as unapproved `<SVC>-PROP-<NNN>` entries and cannot be marked `Approved` until the human maintainer approves the change proposal.

### Deprecation Rules
- Requirements are never deleted or overwritten with unrelated logic.
- When a feature is retired or replaced:
  1. The Lifecycle Status is changed to `Deprecated`.
  2. The requirement description is updated with a notice: `> **Deprecated**: Superseded by <SVC>-FR-<NNN> as of version X.Y.Z.`
  3. The identifier remains permanently reserved to maintain historical auditability.

---

## 4. Contract Compatibility Flags (REST & Events)

In accordance with repository guidelines (e.g., `.github/copilot-instructions.md`), changes to interfaces must maintain backward compatibility whenever possible.

### REST API Contracts
- Changes must declare one of two flags:
  - `[Compatible]`: Field added with default, optional parameter added, new endpoint added.
  - `[Breaking]`: Endpoint path altered, required field added to request, existing response field removed or renamed.
- Unversioned endpoints (such as `/api/inventory/{productCode}`) must maintain compatibility across client versions.

### Event Contracts (Kafka)
- Changes must declare one of two flags:
  - `[Compatible]`: New optional field added to JSON payload, new event type handled gracefully.
  - `[Breaking]`: Topic name changed, partition key strategy modified, required JSON field removed or altered in type.
- Downstream consumer impacts (such as Kafka Streams joins or Outbox bindings) must be documented in the proposal.

---

## 5. Change Proposal (`PROP`) Template

When proposing modifications to existing behavior, fixing suspected defects, or introducing new capabilities, use the following template in `open-questions.md`:

```markdown
### <SVC>-PROP-<NNN>: [Proposal Title]

- **Status**: Draft | Under Review | Approved | Rejected
- **Date Created**: YYYY-MM-DD
- **Author**: [Human Engineer / AI Agent]
- **Target Specification Files**: `api-contracts.md`, `data-model.md`, `workflows.md`
- **Motivating Question / Issue**: `<SVC>-Q-<NNN>`
- **Contract Impact**: `[Compatible]` | `[Breaking]`

#### 1. Problem Statement & Motivation
Describe the deficiency, defect, or new requirement necessitating this change. Reference current behavior and test gaps.

#### 2. Proposed Specification Changes
Detail the exact modifications proposed for requirements, business rules, or contracts:
- **Modified Requirement**: `<SVC>-FR-<NNN>`
- **Updated Acceptance Criteria**: Given / When / Then updates
- **New Invariant**: `<SVC>-BR-<NNN>`

#### 3. Cross-Service & Downstream Impact
Analyze the blast radius of this change across dependent services (e.g., Catalog Service, Order Service, API Gateway).

#### 4. Implementation & Migration Plan
- **Database Migrations**: Liquibase changesets required.
- **Source Modifications**: Classes and methods to be created or adjusted.
- **Test Strategy**: New unit and integration test assertions.
```

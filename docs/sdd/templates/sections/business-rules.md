# Business Rules Specification

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`

This document specifies the domain invariants, validation constraints, calculation logic, and state transition guards governing `<service-name>`.

---

## 1. Business Rules Index *(Mandatory)*

| ID | Title | Enforcement Level | Evidence Status | Primary Source Reference |
| :--- | :--- | :--- | :--- | :--- |
| `<SVC>-BR-001` | Unique Natural Code Invariant | Database / Unique Index | Verified | `path/to/migration.json` |
| `<SVC>-BR-002` | Non-Negative Quantity Rule | Service Layer Logic | Verified | `path/to/Service.java#Service#validate` |

---

## 2. Business Rule Specifications *(Mandatory)*

### <SVC>-BR-001: [Business Rule Title]

- **ID**: `<SVC>-BR-001`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Draft | Approved | Implemented | Verified | Deprecated
- **Evidence Status**: Verified | Implemented | Configured | Inferred | Needs-Clarification
- **Statement**: [Formal declaration of the domain invariant or business rule]
- **Enforcement Mechanism**: Database Constraint | JPA Validation | Domain Service Logic | Kafka Interceptor
- **Source Reference**: `<repo-relative-path>#<ClassName>#<methodName>`
- **Test Reference**: `<repo-relative-path>#<TestClassName>#<testMethodName>` (or `None`)
- **Linked Requirements**: `<SVC>-FR-001`, `<SVC>-FR-002`
- **Description / Context**: [Detailed context, error behavior upon violation, and rationale]

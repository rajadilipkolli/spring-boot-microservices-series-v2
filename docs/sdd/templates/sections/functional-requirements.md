# Functional Requirements Specification

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`

This document defines the functional capabilities offered by `<service-name>`. Every requirement adheres to the standard `<SVC>-FR-<NNN>` template.

---

## 1. Functional Requirements Index *(Mandatory)*

| ID | Title | Lifecycle Status | Evidence Status | Primary Source Reference |
| :--- | :--- | :--- | :--- | :--- |
| `<SVC>-FR-001` | Retrieve Paginated Resources | Approved | Verified | `path/to/Controller.java#Controller#getAll` |
| `<SVC>-FR-002` | Create Resource | Approved | Verified | `path/to/Controller.java#Controller#create` |

---

## 2. Requirement Specifications *(Mandatory)*

### <SVC>-FR-001: [Requirement Title]

- **ID**: `<SVC>-FR-001`
- **Category**: Functional Requirement
- **Lifecycle Status**: Draft | Approved | Implemented | Verified | Deprecated
- **Evidence Status**: Verified | Implemented | Configured | Inferred | Needs-Clarification
- **Description**: [Detailed description of the requirement behavior]
- **Source Reference**: `<repo-relative-path>#<ClassName>#<methodName>`
- **Test Reference**: `<repo-relative-path>#<TestClassName>#<testMethodName>`
- **Linked Business Rules**: `<SVC>-BR-001`
- **Linked Questions / Proposals**: None

#### Scenarios & Acceptance Criteria

##### Scenario 1: [Positive Scenario Title]
- **Given** [System state and valid preconditions]
- **When** [Triggering action is invoked]
- **Then** [Expected state change, returned result, and published events]

##### Scenario 2: [Negative Scenario Title]
- **Given** [Invalid input, missing resource, or boundary condition]
- **When** [Triggering action is invoked]
- **Then** [Expected error code, ProblemDetail body, and state rollback]

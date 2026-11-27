# Requirement Authoring Guidelines

This guideline defines the standard format, structure, and quality standards for authoring Functional Requirements (`FR`), Business Rules (`BR`), Non-Functional Requirements (`NFR`), Open Questions (`Q`), and Proposed Changes (`PROP`).

---

## 1. Testable Authoring Principles

All requirements must be authored in an unambiguous, objective, and testable format:
1. **Unambiguous**: Avoid vague terms like "fast", "reliable", "user-friendly", or "properly". State exact thresholds, HTTP status codes, error models, and data types.
2. **Atomic**: Each requirement must state a single clear capability, constraint, or invariant.
3. **Scenario-Based**: Functional requirements must include both positive (happy path) and negative (error handling, boundary violations, invalid input) scenarios expressed in Given / When / Then format.
4. **Grounded**: Every requirement must link directly to the source code symbols and test cases that substantiate its existence and behavior.

---

## 2. Requirement Specification Blocks

### 2.1 Functional Requirement (`<SVC>-FR-<NNN>`) Template

Each functional requirement must use the following standard block:

```markdown
### <SVC>-FR-<NNN>: [Short Descriptive Title]

- **ID**: `<SVC>-FR-<NNN>`
- **Category**: Functional Requirement
- **Lifecycle Status**: Draft | Approved | Implemented | Verified | Deprecated
- **Evidence Status**: Verified | Implemented | Configured | Inferred | Needs-Clarification
- **Description**: Detailed description of the functionality provided to consumers or callers.
- **Source Reference**: `<repo-relative-path>#<ClassName>#<methodName>`
- **Test Reference**: `<repo-relative-path>#<TestClassName>#<testMethodName>` (or `None`)
- **Linked Business Rules**: `<SVC>-BR-<NNN>`, `<SVC>-BR-<MMM>`
- **Linked Questions / Proposals**: `<SVC>-Q-<NNN>` / `<SVC>-PROP-<NNN>`

#### Scenarios & Acceptance Criteria

##### Scenario 1: [Positive / Happy Path Title]
- **Given** [preconditions, system state, stored data]
- **When** [action invoked, endpoint called, event received with parameters]
- **Then** [expected outcome, return value, database updates, published events]

##### Scenario 2: [Negative / Edge Case / Error Handling Title]
- **Given** [preconditions, invalid payload, or boundary state]
- **When** [action invoked with invalid input or missing resource]
- **Then** [expected RFC 7807 ProblemDetail or HTTP error status, no state change]
```

---

### 2.2 Business Rule (`<SVC>-BR-<NNN>`) Template

Business rules represent invariant domain logic, calculations, lifecycle constraints, or validation rules that govern the state of domain entities.

```markdown
### <SVC>-BR-<NNN>: [Short Descriptive Invariant Title]

- **ID**: `<SVC>-BR-<NNN>`
- **Category**: Business Rule / Invariant
- **Lifecycle Status**: Draft | Approved | Implemented | Verified | Deprecated
- **Evidence Status**: Verified | Implemented | Configured | Inferred | Needs-Clarification
- **Statement**: Formal declarative statement of the business invariant or domain rule.
- **Enforcement Mechanism**: Database Constraint | JPA Entity / Validation | Service Logic | Kafka Consumer Logic
- **Source Reference**: `<repo-relative-path>#<ClassName>#<methodName>`
- **Test Reference**: `<repo-relative-path>#<TestClassName>#<testMethodName>` (or `None`)
- **Linked Requirements**: `<SVC>-FR-<NNN>`
```

---

### 2.3 Non-Functional Requirement (`<SVC>-NFR-<NNN>`) Template

Non-functional requirements specify operational qualities, performance thresholds, availability constraints, and security measures.

```markdown
### <SVC>-NFR-<NNN>: [Short Descriptive Quality Title]

- **ID**: `<SVC>-NFR-<NNN>`
- **Category**: Non-Functional Requirement
- **Dimension**: Performance | Scalability | Reliability | Security | Observability | Maintainability
- **Lifecycle Status**: Draft | Approved | Implemented | Verified | Deprecated
- **Evidence Status**: Target | Configured | Verified | Inferred | Needs-Clarification
- **Metric / Threshold**: Concrete quantifiable metric (e.g., P99 latency < 500ms, 99.9% uptime).
- **Source Reference**: `<repo-relative-path>#<filename>` (e.g., k8s manifests, application.yml)
- **Reference Document**: Pointer to operational baseline or runbook (e.g., `docs/runbooks/SLO-Definitions.md`)
```

---

## 3. Suspected Defects vs. Proposed Changes

When documenting existing systems during reverse-specification or pilot phases, authors and AI agents will inevitably encounter suspected defects, architectural contradictions, or code bugs.

> [!WARNING]
> **Strict Defect Handling Rule**:
> 1. Suspected defects must be documented as **current observed behavior** within the specification, assigned an appropriate evidence status (e.g., `Implemented`, `Inferred`, or `Needs-Clarification`).
> 2. The suspected defect must be formally registered in `open-questions.md` as an Open Question entry: `<SVC>-Q-<NNN>`.
> 3. Any proposed bug fix, architectural refactoring, or behavioral correction must be formulated **only** as an unapproved Proposed Change entry: `<SVC>-PROP-<NNN>`.
> 4. **Do not modify production source code or test behavior** during specification authoring. Code modifications are permitted only after an `<SVC>-PROP-<NNN>` has been formally reviewed, accepted, and approved.

### Format for Questions (`<SVC>-Q-<NNN>`)
```markdown
### <SVC>-Q-<NNN>: [Question / Issue Summary]
- **Category**: Discrepancy | Suspected Defect | Missing Specification | Test Gap | Inferred Behavior
- **Context**: Explanation of current observed behavior and why it appears contradictory, buggy, or unverified.
- **Evidence**: Source references, test references, or configuration snippets.
- **Linked Proposal**: `<SVC>-PROP-<NNN>` (optional pointer to suggested resolution)
- **Status**: Open | Under Review | Resolved
```

### Format for Proposals (`<SVC>-PROP-<NNN>`)
```markdown
### <SVC>-PROP-<NNN>: [Proposal Summary]
- **Status**: Draft | Under Review | Approved | Rejected
- **Motivating Question**: `<SVC>-Q-<NNN>`
- **Proposed Behavior**: Precise specification of the new intended behavior or contract change.
- **Impact Analysis**: Affected consumers, database migrations, breakages, and backward compatibility.
- **Implementation Strategy**: Changes required in source, tests, and configuration.
```

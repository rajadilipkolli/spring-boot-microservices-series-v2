# Requirements Traceability Matrix

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`

This document provides a single consolidated traceability matrix connecting every Functional Requirement (`FR`), Business Rule (`BR`), and Non-Functional Requirement (`NFR`) to its specification section, production source code symbol, automated test method, and evidence status.

---

## 1. Traceability Table *(Mandatory)*

| Requirement ID | Specification Section | Source Symbol | Test Reference | Evidence Status |
| :--- | :--- | :--- | :--- | :--- |
| `<SVC>-FR-001` | [Functional Requirements](functional-requirements.md) | `path/to/Class.java#Class#method` | `path/to/Test.java#Test#testMethod` | Verified |
| `<SVC>-BR-001` | [Business Rules](business-rules.md) | `path/to/Entity.java#Entity` | `path/to/Test.java#Test#testMethod` | Verified |
| `<SVC>-NFR-001` | [Non-Functional Requirements](non-functional-requirements.md) | `path/to/manifest.yaml` | None | Configured |

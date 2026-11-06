# Validation Guidelines

This guideline defines the manual validation procedure to verify specification completeness, structural integrity, source alignment, and traceability coverage before merging.

> [!NOTE]
> Currently, validation is performed manually according to this checklist. Automated linting and validation tooling for SDD specifications may be evaluated as a future enhancement.

---

## 1. Manual Validation Checklist

Every service specification under `docs/sdd/services/<service>/` must be verified against this 7-point checklist:

### 1.1 Requirement Completeness & Formatting
- [ ] Every requirement block adheres to the `<SVC>-<CAT>-<NNN>` convention.
- [ ] Category codes match standard prefixes: `FR`, `BR`, `NFR`, `Q`, `PROP`.
- [ ] No duplicated IDs or renumbered sequences exist.
- [ ] All functional requirements contain both positive (happy path) and negative (error handling/boundary) Given/When/Then scenarios.
- [ ] Every requirement explicitly declares both a `Lifecycle Status` and an `Evidence Status`.

### 1.2 Grounded Source and Test References
- [ ] All source references use repository-relative paths with class and method anchors (`path/to/File.java#ClassName#methodName`).
- [ ] **No line numbers** are present in any source or test references.
- [ ] All referenced classes, methods, annotations, and Liquibase changesets exist in the repository.
- [ ] Test method names referenced in specifications match exact Java test method names.

### 1.3 Link Integrity and Relative Path Resolution
- [ ] All markdown links between specification sections resolve correctly using relative paths (e.g., `[Business Rules](business-rules.md)`).
- [ ] Cross-references to project ADRs (`docs/adr/`) and runbooks (`docs/runbooks/`) resolve to existing files.
- [ ] Links to standalone Mermaid diagram files under `diagrams/` resolve correctly.

### 1.4 Mermaid Diagram Syntax and Rendering
- [ ] All Mermaid diagrams enclosed in ````mermaid` code blocks render without syntax errors in GitHub markdown preview.
- [ ] Node labels containing special characters, brackets, or parentheses are properly quoted (e.g., `id["Label (Info)"]`).
- [ ] Diagram elements reflect actual code reality (e.g., correct Kafka topic names and consumer groups).

### 1.5 Inferred and Clarification Claims
- [ ] Any claim deduced from framework defaults or lacking explicit configuration is explicitly marked as `Inferred`.
- [ ] Any ambiguous, conflicting, or unverified behavior is marked as `Needs-Clarification` and linked to an `<SVC>-Q-<NNN>` entry in `open-questions.md`.
- [ ] Suspected defects are recorded as current observed behavior, with fixes placed solely in unapproved `<SVC>-PROP-<NNN>` proposals.

### 1.6 Traceability Coverage
- [ ] Every `<SVC>-FR-<NNN>`, `<SVC>-BR-<NNN>`, and `<SVC>-NFR-<NNN>` has a corresponding entry in `traceability-matrix.md`.
- [ ] The traceability matrix includes specification section, source symbol, test reference (or "none"), and evidence status.
- [ ] No unmapped requirements exist.

### 1.7 Service Agnosticism of Templates
- [ ] All files under `docs/sdd/templates/` remain strictly service-agnostic, free of hardcoded service-specific tables, routes, or database schemas, except where clearly labeled as generic illustrative examples.

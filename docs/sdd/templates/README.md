# Service Specification Template

This directory contains reusable, service-agnostic templates for authoring a complete microservice specification under `docs/sdd/services/<service-name>/`.

---

## Template Structure

When creating a new service specification, copy the section templates from `sections/` and diagram templates from `diagrams/` into the new service directory:

```
docs/sdd/services/<service-name>/
├── README.md                      # Instantiated from sections/service-overview.md
├── domain-model.md                # Domain entities, DTOs, ubiquitous glossary
├── functional-requirements.md     # Functional requirements (<SVC>-FR-<NNN>)
├── business-rules.md              # Domain business rules and invariants (<SVC>-BR-<NNN>)
├── api-contracts.md               # Synchronous REST/HTTP endpoint contracts
├── data-model.md                  # Database schema, migrations, tables, transaction boundaries
├── workflows.md                   # Core business processes, sagas, state machines
├── integrations.md                # Asynchronous messaging (Kafka), event schemas, outbox
├── architecture.md                # Modularity, components, dependencies, technology stack
├── non-functional-requirements.md # Quality attributes, SLOs, security, latency targets
├── operations.md                  # Profiles, config, Kubernetes manifests, runbooks
├── testing.md                     # Test coverage mapping, test suite inventory
├── traceability-matrix.md         # Matrix mapping FR/BR/NFR to source and tests
├── open-questions.md              # Open questions (<SVC>-Q-<NNN>) & proposals (<SVC>-PROP-<NNN>)
└── diagrams/                      # Visual Mermaid diagram sources
    ├── context-diagram.md         # System context
    ├── component-diagram.md       # Component architecture
    ├── er-diagram.md              # Entity-Relationship diagram
    ├── saga-sequence-diagram.md   # Distributed transaction saga sequence
    └── state-diagram.md           # Entity lifecycle state machine
```

---

## Instructions for Template Usage
1. Replace all `<SVC>` placeholders with the registered 3-to-4 character service prefix (e.g., `INV`, `ORD`, `CAT`).
2. Fill in metadata, descriptions, source references, and test references.
3. Observe the mandatory vs optional markers on individual subsections.
4. Keep requirements atomic, scenario-based, and grounded in codebase facts.

5. Copy diagrams into `diagrams/`; change section-template links from `../diagrams/` to `diagrams/` after placing sections in the service root. Verify every relative link.

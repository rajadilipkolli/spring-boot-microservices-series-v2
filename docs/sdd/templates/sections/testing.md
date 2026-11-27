# Testing and Verification Specification

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`

This document outlines the testing strategy, test suite inventory, execution commands, and requirement test coverage for `<service-name>`.

---

## 1. Test Strategy & Infrastructure *(Mandatory)*

- **Build Tool**: Maven Wrapper (`./mvnw` or `mvnw.cmd`).
- **Assertion Library**: AssertJ (`org.assertj.core.api.Assertions.assertThat`).
- **Integration Test Environment**: Testcontainers (PostgreSQL, Kafka, Schema Registry).
- **Execution Command**:
  ```bash
  ./mvnw -pl <service-name> verify
  ```

---

## 2. Test Suite Inventory *(Mandatory)*

| Test Class | Scope / Category | Description |
| :--- | :--- | :--- |
| `[Class]Test.java` | Unit Test | Fast, isolated tests mocking dependencies. |
| `[Class]IT.java` | Integration Test | Full Spring context with Testcontainers. |
| `ModularityTests.java` | Architecture Test | Spring Modulith verification of architectural rules. |

---

## 3. Discrepancies and Misleading Test Names *(Mandatory)*
[Document any test method whose name contradicts what it actually tests, or tests that pass trivially without asserting business invariants.]

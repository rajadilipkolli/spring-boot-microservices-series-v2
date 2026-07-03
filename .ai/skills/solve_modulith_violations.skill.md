---
name: Solve Spring Modulith Violations
description: Enforce architectural rules and resolve Spring Modulith and ArchUnit violations.
---

# Skill: Solve Spring Modulith Violations

This skill provides a procedure for identifying, diagnosing, and resolving architectural violations in Spring Modulith-managed services.

## Rules
- **Encapsulation**: Only the root package of a module is part of its public API. Internal packages must not be accessed from outside.
- **Layering**: Circular dependencies between modules are strictly prohibited.
- **Visibility**: Repositories and internal helpers should be package-private to force interaction through a module's public service boundary.

## Procedure

### 1. Run Verification
Execute the modulith/architecture verification test in the targeted service:
```powershell
.\mvnw.cmd test -Dtest=ModulithTest
# Or for ArchUnit:
# .\mvnw.cmd test -Dtest=*Arch*
```
*Note: Ensure you have a test class annotated with `@ApplicationModuleTest` or using `ApplicationModules`.*

### 2. Analyze the Report
Check the `target/modulith` directory (if configured) or the console output.
Typical violations include:
- **Illegal Dependency**: Module A is attempting to access a class in a sub-package of Module B. (Package A depends on Package B's internal implementation).
- **Dependency Paradox / Cycle**: A -> B and B -> A cycle detected (Package A -> Package B -> Package A).

### 3. Resolution Strategies
- **Extract API / Make it Public**: Move common types to the root package of a module (which is public by default in Modulith), or extract shared interfaces into a public `api` package. Keep implementations internal.
- **Event-Driven Coupling**: Replace direct synchronous service calls between modules with Spring Modulith's `ApplicationEventPublisher` to break cycles.
- **Visibility Correction**: Change public classes to package-private if they are not intended to be shared.

### 4. Code Compliance
Ensure all shared components follow these rules:
- **Repositories**: Should typically be package-private unless cross-module access is explicitly allowed via an API.
- **Controllers**: Always in a `web` or `controller` package, depending on the service.
- **Entities**: Keep them encapsulated within the domain module where possible.

## Verification
Confirm the fix by running the verification command again. No violations should be reported.

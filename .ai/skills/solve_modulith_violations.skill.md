# Skill: Solve Spring Modulith Violations

This skill provides a procedure for identifying and resolving architectural violations in Spring Modulith-managed services.

## Procedure

### 1. Run Verification
Execute the modulith verification test in the targeted service:
```powershell
.\mvnw.cmd test -Dtest=ModulithTest
```
*Note: Ensure you have a test class annotated with `@ApplicationModuleTest` or using `ApplicationModules`.*

### 2. Analyze the Report
Check the target/modulith directory (if configured) or the console output.
Typical violations include:
- **Illegal Dependency**: Package A depends on Package B's internal implementation.
- **Cycle**: Package A -> Package B -> Package A.

### 3. Resolution Strategies
- **Make it Public**: Move common types to the root package of a module (which is public by default in Modulith).
- **Use Published Events**: Replace direct service calls with `ApplicationEventPublisher`.
- **Refactor to API Package**: Extract shared interfaces into a public `api` package.

### 4. Code Compliance
Ensure all shared components follow these rules:
- **Repositories**: Should typically be package-private unless cross-module access is explicitly allowed via an API.
- **Controllers**: Always in a `web` or `controller` package, depending on the service.
- **Entities**: Keep them encapsulated within the domain module where possible.

## Verification
Rerun the test. If it passes, the architectural integrity is preserved.

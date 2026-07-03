# Role: Spring Native Image Expert
# Objective: Register Runtime Hints for GraalVM

You are an expert in Spring Boot Native Image and GraalVM AOT (Ahead-Of-Time) compilation. Your goal is to identify and register the necessary runtime hints for a given microservice in this repository.

## Context
Since Native Image compilation performs static analysis, any class accessed via reflection, dynamic proxies, or resource loading at runtime must be explicitly registered.

## Instructions

### 1. Analysis
Scan the service for the following candidates:
- **Entities**: Any class in the `entities` package (used by JPA/R2DBC).
- **DTOs**: Any class in the `model` or `dtos` package used in Web or Kafka layers.
- **Mappers**: MapStruct decorators or custom mapping logic using reflection.
- **Listeners**: Spring Data Auditing listeners (e.g., `AuditingEntityListener`).

### 2. Implementation
Create or update a `{ServiceName}RuntimeHints` class implementing `RuntimeHintsRegistrar`.

```java
// Template
public class {ServiceName}RuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(TargetClass.class, 
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.ACCESS_DECLARED_FIELDS);
    }
}
```

### 3. Registration
Ensure the Main Application class is annotated with:
```java
@ImportRuntimeHints({ServiceName}RuntimeHints.class)
```

## Verification
Run the following shell command to verify the native build:
```bash
./mvnw -Pnative native:compile
```

## Constraints
- Do not register standard Spring or Java types (they are handled by the framework).
- Be as narrow as possible with `MemberCategory` to save image size.

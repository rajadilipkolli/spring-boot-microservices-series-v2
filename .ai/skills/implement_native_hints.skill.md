---
name: Implement Spring Native Hints
description: Identify and register runtime hints for GraalVM native image compilation.
---

# Skill: Implement Spring Native Hints

This skill provides a structured approach for implementing Spring Native hints in this repository. Since Native Image compilation performs static analysis, any class accessed via reflection, dynamic proxies, or resource loading at runtime must be explicitly registered.

## Prerequisites
- Spring Boot 3.x or 4.x
- `org.graalvm.buildtools:native-maven-plugin` included in the `pom.xml`.

## Step-by-Step Instructions

### 1. Identify Classes Needing Hints
Scan the service for the following candidates that are reachable at runtime via reflection, dynamic proxies, or resource loading:
- **Entities**: Any class in the `entities` package (used by JPA/R2DBC). Reflection for fields and setters.
- **DTOs**: Any class in the `model` or `dtos` package used in Web or Kafka layers. Reflection for Jackson serialization/deserialization.
- **Mappers (MapStruct)**: Especially Decorators or custom mapping logic using reflection.
- **Custom Exceptions**: If they carry state that needs binding.
- **Listeners**: Spring Data Auditing listeners (e.g., `AuditingEntityListener`).

### 2. Create the RuntimeHintsRegistrar
Create or update a class in the `config` package named `{ServiceName}RuntimeHints` implementing `RuntimeHintsRegistrar`.

```java
package com.example.{servicename}.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class {ServiceName}RuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Register reflection hints for entities/DTOs
        hints.reflection().registerType(TargetClass.class, 
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.ACCESS_DECLARED_FIELDS);
            
        // Register proxy hints if using JDK Dynamic Proxies
        // hints.proxies().registerJdkProxy(MyInterface.class);
    }
}
```

### 3. Register the Registrar
Add the `@ImportRuntimeHints` annotation to your main `@SpringBootApplication` class.

```java
@SpringBootApplication
@ImportRuntimeHints({ServiceName}RuntimeHints.class)
public class {ServiceName}Application { ... }
```

### 4. Verify the Build
Run the native compilation to verify:
```powershell
.\mvnw.cmd -Pnative native:compile
```

## Best Practices & Constraints
- **Be Specific**: Only register what is necessary.
- **Use MemberCategories**: Minimize reflection surface by specifying exactly what's needed (e.g., just constructors). Be as narrow as possible to save image size.
- **Group Hints**: Group by logical category (Entities, Serdes, Service handlers).
- **Framework Types**: Do not register standard Spring or Java types (they are handled by the framework).

# Skill: Implement Spring Native Hints

This skill provides a structured approach for implementing Spring Native hints in this repository.

## Prerequisites
- Spring Boot 3.x or 4.x
- `org.graalvm.buildtools:native-maven-plugin` included in the `pom.xml`.

## Step-by-Step Instructions

### 1. Identify Classes Needing Hints
Any class reachable at runtime via reflection, dynamic proxies, or resource loading needs hints. Common candidates include:
- **JPA Entities**: Reflection for fields and setters.
- **DTOs**: Reflection for Jackson serialization/deserialization.
- **Mappers (MapStruct)**: Especially Decorators.
- **Custom Exceptions**: If they carry state that needs binding.
- **Auditing Listeners**: E.g., `AuditingEntityListener`.

### 2. Create the RuntimeHintsRegistrar
Create a class in the `config` package named `{ServiceName}RuntimeHints`.

```java
package com.example.{servicename}.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class {ServiceName}RuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Register reflection hints for entities/DTOs
        hints.reflection().registerType(MyClass.class, 
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.ACCESS_DECLARED_FIELDS);
            
        // Register proxy hints if using JDK Dynamic Proxies
        // hints.proxies().registerJdkProxy(MyInterface.class);
    }
}
```

### 3. Register the Registrar
Add the `@ImportRuntimeHints` annotation to your `@SpringBootApplication` class.

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

## Best Practices
- **Be Specific**: Only register what is necessary.
- **Use MemberCategories**: Minimize reflection surface by specifying exactly what's needed (e.g., just constructors).
- **Group Hints**: Group by logical category (Entities, Serdes, Service handlers).

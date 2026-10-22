# Architecture testing using ArchUnit

To enforce architecture rules using ArchUnit in a Spring Boot application, follow the below steps:

## Add Taikai dependency
Taikai is an extension of the popular ArchUnit library, offering a comprehensive suite of predefined rules tailored for various technologies.

Add the following dependency:

```xml
<properties>
    <taikai.version>1.60.0</taikai.version>
</properties>

<dependencies>
    <dependency>
      <groupId>com.enofex</groupId>
      <artifactId>taikai</artifactId>
      <version>${taikai.version}</version>
      <scope>test</scope>
    </dependency>
</dependencies>
```

## Write ArchUnit Test

Write `ArchUnitTests` as follows:

```java
import com.enofex.taikai.Taikai;
import com.enofex.taikai.java.ImportsConfigurer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;

import static com.tngtech.archunit.core.domain.JavaModifier.*;

class ArchUnitTests {
    private static final String BASE_PACKAGE = "com.sivalabs.demo"; //replace this with actual base package

    @Test
    void shouldFulfillConstraints() {
        Taikai.builder()
                .namespace(BASE_PACKAGE)
                .java(java -> java
                        .noUsageOfDeprecatedAPIs()
                        .methodsShouldNotDeclareGenericExceptions()
                        .utilityClassesShouldBeFinalAndHavePrivateConstructor()
                        .imports(ImportsConfigurer::shouldHaveNoCycles)
                        .naming(naming -> naming
                                .fieldsShouldNotMatch(".*(List|Set|Map)$")
                                .constantsShouldFollowConventions()
                                .interfacesShouldNotHavePrefixI()))
                .logging(logging -> logging
                        .loggersShouldFollowConventions(Logger.class, "LOG", List.of(PRIVATE, STATIC, FINAL)))
                .test(test -> test
                        .junit(junit -> junit
                                .classesShouldBePackagePrivate(".*Test(s)")
                                .classesShouldNotBeAnnotatedWithDisabled()
                                .methodsShouldNotBeAnnotatedWithDisabled()))
                .spring(spring -> spring
                        .noAutowiredFields()
                        .boot(boot -> boot
                                .applicationClassShouldResideInPackage(BASE_PACKAGE))
                        .configurations(c -> c.namesShouldMatch(".+Config"))
                        .controllers(controllers -> controllers
                                .shouldBeAnnotatedWithRestController()
                                .namesShouldEndWithController()
                                .shouldNotDependOnOtherControllers()
                                .shouldBePackagePrivate())
                        .services(services -> services
                                .shouldBeAnnotatedWithService()
                                .shouldNotDependOnControllers()
                                .namesShouldEndWithService())
                        .repositories(repositories -> repositories
                                .shouldNotDependOnServices()
                                .namesShouldEndWithRepository()))
                .build()
                .checkAll();
    }
}
```
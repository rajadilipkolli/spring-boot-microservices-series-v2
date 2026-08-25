# Native Support Deferred for Service Registry (Eureka Server)

Native compilation for the `service-registry` module has been deferred.

## Reason for Deferral

Despite adding `reachability-metadata.json` hints via the GraalVM tracing agent and setting up a `RuntimeHintsRegistrar`, the native compilation process fails during the Spring Boot `process-aot` (Ahead-of-Time code generation) phase.

The failure is caused by Spring Cloud Netflix Eureka's auto-configuration heavily relying on `RefreshScope` for the `eurekaClient` bean (`EurekaClientAutoConfiguration$RefreshableEurekaClientConfiguration`). Spring Boot AOT code generation currently does not support `RefreshScope`, throwing an `UnsupportedTypeValueCodeGenerationException`.

## Error Trace
```text
Exception in thread "main" org.springframework.beans.factory.aot.AotBeanProcessingException: Error processing bean with name 'eurekaClient' defined in BeanDefinition defined in class path resource [org/springframework/cloud/netflix/eureka/EurekaClientAutoConfiguration$RefreshableEurekaClientConfiguration.class]: failed to generate code for bean definition
...
Caused by: org.springframework.aot.generate.UnsupportedTypeValueCodeGenerationException: Code generation does not support org.springframework.cloud.context.scope.refresh.RefreshScope
```

## Next Steps
Until Spring Cloud provides an AOT-compatible alternative for Eureka Server, the `service-registry` module will continue to be packaged and deployed as a standard JVM image. The GitHub Actions workflow (`.github/workflows/service-registry.yml`) has been left intact to build the standard JVM image.

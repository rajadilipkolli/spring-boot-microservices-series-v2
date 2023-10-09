# API-GATEWAY

Spring Cloud Gateway is a web application gateway built on top of the Spring framework. It provides a simple, yet powerful way to route requests to different services based on various criteria, such as URI, host, and header values. It also offers features such as load balancing, rate limiting, and circuit breaking to help manage the flow of traffic to your services. Spring Cloud Gateway can be easily integrated with other Spring Cloud services, such as Eureka and Hystrix, to provide a robust, scalable, and secure platform for building microservices-based applications.

### Run tests

```shell
./mvnw clean verify
```

### Run locally

```shell
docker-compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Using Testcontainers at Development Time
You can run `TestAPIGatewayApplication.java` from your IDE directly.
You can also run the application using Maven as follows:

```shell
./mvnw spotless:apply spring-boot:test-run
```

## URL's

Make sure you are using the right URLs when calling ?

### Discovery (spring.cloud.gateway.discovery.locator.enabled=true)

http://localhost:8765/CATALOG-SERVICE/catalog-service/api/

###LowerCase (spring.cloud.gateway.discovery.locator.lower-case-service-id=true)

http://localhost:8765/catalog-service/catalog-service/api/

### Discovery Disabled and Custom Routes Configured

http://localhost:8765/catalog-service/api/

### Swagger URL
http://localhost:8765/api-gateway/swagger-ui.html

#### useful URLS

 - http://localhost:8765/api-gateway/actuator/gateway/routes - find all defined routes
 - http://localhost:8765/api-gateway/actuator/gateway/globalfilters - list global filters
 - http://localhost:8765/api-gateway/actuator/gateway/routefilters - list route filters
 - http://localhost:8765/api-gateway/actuator/gateway/routes/catalog-service - Details about the service

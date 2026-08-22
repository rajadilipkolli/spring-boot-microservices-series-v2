# catalogservice

### Run locally

```shell
docker-compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Using Testcontainers at Development Time
You can run `TestCatalogServiceApplication.java` from your IDE directly.
You can also run the application using Maven as follows:

```shell
./mvnw spotless:apply spring-boot:test-run
```

### Run tests

```shell
./mvnw clean verify
```

### Running only this Service Locally - Tips

To run only the Catalog Service locally with clean logs, you can follow these steps:


1. start the Kafka and Postgres servers by using below command(You should be inside appropriate directory and docker setup should be done :-)
```shell
docker compose up kafka postgres
```
2. In IntelIj Open Modify Run Configuration from Main class:
        `com.example.catalogservice.CatalogServiceApplication`
Set the Environment variable value to 
```text
SPRING_PROFILES_ACTIVE=local
```


### Useful Links
* Swagger UI: http://localhost:18080/catalog-service/swagger-ui.html
* Actuator Endpoint: http://localhost:18080/catalog-service/actuator
* Actuator Health Endpoint : http://localhost:18080/catalog-service/actuator/health
* Grafana Dashboard: http://localhost:3000

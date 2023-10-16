# inventory-service

### Run tests
`$ ./mvnw clean verify`

### Run locally

```shell
docker-compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Using Testcontainers at Development Time
You can run `TestInventoryApplication.java` from your IDE directly.
You can also run the application using Maven as follows:

```shell
./mvnw spotless:apply spring-boot:test-run
```

### Running only this Service Locally - Tips

To run only the Inventory Service locally with clean logs, you can follow these steps:


1. start the Kafka and Postgres servers by using below command(You should be inside appropriate directory and docker setup should be done :- ) :
```shell
docker compose up kafka postgres zipkin-server
```

2. In IntelIj Open Modify Run Configuration from Main class:
        `com.example.inventoryservice.InventoryServiceApplication`
Set the Environment variable value to 
```text
SPRING_PROFILES_ACTIVE=local
```


### Useful Links
* Swagger UI: http://localhost:18181/inventory-service/swagger-ui.html
* Actuator Endpoint: http://localhost:18181/inventory-service/actuator

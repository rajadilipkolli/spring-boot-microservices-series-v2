# order-service

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
You can run `TestApplication.java` from your IDE directly.
You can also run the application using Maven as follows:

```shell
./mvnw spotless:apply spring-boot:test-run
```


### Useful Links
* Swagger UI: http://localhost:18282/order-service/swagger-ui.html
* Actuator Endpoint: http://localhost:18282/order-service/actuator
* Catalog Service : http://localhost:18080/catalog-service/swagger-ui.html

## Running only this Service Locally - Tips

To run only the Order Service locally with clean logs, you can follow these steps:



1.start the Kafka and Postgres servers by using below command(You should be inside appropriate directory and docker setup should be done :- ) :
```shell
docker compose up kafka postgres
```
2.In IntelIj Open Modify Run Configuration from Main class:
        `com.example.orderservice.OrderServiceApplication`
Set the Environment variable value to 
```text
SPRING_PROFILES_ACTIVE=local
```

3.In case local profile doesn't due to any issues possible not able to connect to postgresDB
Modify the Environment Variable Value as below this brings application up by connecting to H2 database.
```text
SPRING_PROFILES_ACTIVE=h2
```


### Notes
* KafkaStream DeadLetter is configured in `KafkaStreamsConfig.java`

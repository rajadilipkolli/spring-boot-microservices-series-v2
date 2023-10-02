# order-service

### Run tests
`$ ./mvnw clean verify`

### Run locally
```shell
./mvnw docker:start spring-boot:run
```


### Useful Links
* Swagger UI: http://localhost:18282/order-service/swagger-ui.html
* Actuator Endpoint: http://localhost:18282/order-service/actuator
* Catalog Service : http://localhost:18080/catalog-service/swagger-ui.html

## Running only this Service Locally - Tips

To run only the Order Service locally with clean logs, you can follow these steps:

1. Open the `pom.xml` file in your project.

2. Locate the following dependencies and comment them:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

3.Spin up the Kafka and Postgres server by using below command:
```shell
docker compose up kafka postgres
```
4.In IntelIj Open Modify Run Configuration from Main class:
        com.example.orderservice.OrderServiceApplication
Set the Environment variable value to SPRING_PROFILES_ACTIVE=local

5.In case local profile doesn't due to any issues possible not able to connect to postgresDB
Modify the Environment Variable Value to SPRING_PROFILES_ACTIVE=local1 this brings application up by connecting to H2 database.

6.Inorder to run only this service we need to comment the catalog service calls inside Order service class
```text
       //While working in local independently with out kafka service and catlog service please comment if condition.
//        if (productsExistsAndInStock(productCodes)) {

//        } else {
//            throw new ProductNotFoundException(productCodes);
//        }

```

### Notes
* KafkaStream DeadLetter is configured in `KafkaStreamsConfig.java`

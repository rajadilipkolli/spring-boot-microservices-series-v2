# payment-service

### Run tests
`$ ./mvnw clean verify`

### Run locally
```
$ docker-compose -f docker/docker-compose.yml up -d
$ ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```


### Useful Links
* Swagger UI: http://localhost:18085/payment-service/swagger-ui.html
* Actuator Endpoint: http://localhost:18085/payment-service/actuator

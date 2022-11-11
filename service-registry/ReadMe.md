#service-registry

### Run tests
```shell
./mvnw clean verify
```

### Run locally
```shell
docker compose -f docker-compose.yml up -d
./mvnw spring-boot:run
```

### Useful Links
* Accessing Eureka Server via http://localhost:8761/

#service-registry

A service registry is a central directory that maintains a list of all the available microservices in a system. It allows services to discover and communicate with each other, enabling them to work together to achieve a common goal. The registry typically contains information about the location, availability, and capabilities of each service. It may also include details such as the version of the service, the protocols it uses to communicate, and any dependencies it has on other services. Service registries are an important component of microservice architecture as they enable services to be flexible and scalable, while still being able to interact with each other in a consistent and reliable manner.

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

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/rajadilipkolli/spring-boot-microservices-series-v2)

Code generated using [springboot generator](https://github.com/sivaprasadreddy/generator-springboot)

To run in local first build all services using command 

mvnw clean spotless:apply spring-boot:build-image -DskipTests

To start docker using 

docker-compose up

To start silently use docker-compose -d up , which starts in detached mode

### Starting infrastructure 

docker compose up rabbitmq zipkin-server postgresql mongodb kafka config-server naming-server

### URLS to access services
 - Zipkin : http://localhost:9411/zipkin/
 - RabbitMq : http://localhost:15672/
 - Service Registry : http://localhost:8761
 - API Gateway : http://localhost:8765/api-gateway/swagger-ui.html

 #### References
  - https://piotrminkowski.com/2022/01/24/distributed-transactions-in-microservices-with-kafka-streams-and-spring-boot/
  
#### Projects unable to convert to native Image OOTB
 - config-server
 - api-gateway

#### Breaking Changes in 3.0
 - Migration to jakarta namespace from javax
 - Spring Cloud Seluth is deprecated in favor of Micrometer
 - With New Observability we cant use Rabbit as sender type and use asynchronous communication
 
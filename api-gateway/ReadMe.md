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
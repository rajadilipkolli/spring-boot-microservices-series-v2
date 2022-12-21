# CONFIGURATION SERVER

Configuration server in microservices is a centralized service that stores and manages the configuration data for all the microservices in an application. This configuration data includes settings such as database connections, service dependencies, and environment-specific variables.

The configuration server is typically accessed via a REST API and can be accessed by all the microservices in the application. This allows for easy management and modification of the configuration data without the need to update and redeploy individual microservices.

The configuration server also helps to decouple the microservices from the environment in which they are deployed. This means that the microservices can be easily moved between different environments (e.g. development, staging, production) without the need to change the configuration data within each microservice.

How to access the values of particular project

http://localhost:8888/{projectname}/{profile}
 
Ex: http://localhost:8888/catalog-service/default

## Actuator Endpoints
 - http://localhost:8888/actuator/health
 - http://localhost:8888/actuator/info (done using maven build plugin and git info plugin)
 - http://localhost:8888/actuator/health/configServer

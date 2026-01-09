# Script to start all required services for the microservices example
# Run this script from the root of the spring-boot-microservices-series-v2 directory

# Start Service Registry (Eureka)
Write-Host "Starting Service Registry (Eureka)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "cd service-registry; ./mvnw spring-boot:run"
Write-Host "Waiting for Service Registry to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Start Config Server
Write-Host "Starting Config Server..." -ForegroundColor Green
Start-Process powershell -ArgumentList "cd config-server; ./mvnw spring-boot:run"
Write-Host "Waiting for Config Server to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 20

# Start API Gateway
Write-Host "Starting API Gateway..." -ForegroundColor Green
Start-Process powershell -ArgumentList "cd api-gateway; ./mvnw spring-boot:run"
Write-Host "Waiting for API Gateway to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 20

# Start Catalog Service
Write-Host "Starting Catalog Service..." -ForegroundColor Green
Start-Process powershell -ArgumentList "cd catalog-service; ./mvnw spring-boot:run"
Write-Host "Waiting for Catalog Service to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Start Inventory Service
Write-Host "Starting Inventory Service..." -ForegroundColor Green
Start-Process powershell -ArgumentList "cd inventory-service; ./mvnw spring-boot:run"
Write-Host "Waiting for Inventory Service to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Start Order Service
Write-Host "Starting Order Service..." -ForegroundColor Green
Start-Process powershell -ArgumentList "cd order-service; ./mvnw spring-boot:run"
Write-Host "Waiting for Order Service to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Start Payment Service
Write-Host "Starting Payment Service..." -ForegroundColor Green
Start-Process powershell -ArgumentList "cd payment-service; ./mvnw spring-boot:run"
Write-Host "Waiting for Payment Service to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host "All services should now be running!" -ForegroundColor Green
Write-Host "You can access the Eureka dashboard at: http://localhost:8761" -ForegroundColor Cyan
Write-Host "You can test the API Gateway endpoint at: http://localhost:8765/api/generate" -ForegroundColor Cyan

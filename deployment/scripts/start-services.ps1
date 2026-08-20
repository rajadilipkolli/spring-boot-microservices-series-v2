# Script to start the backend microservices (excludes retail-store-webapp)
# Run this script from the root of the spring-boot-microservices-series-v2 directory

function Start-ServiceProcess {
    param(
        [string]$Name,
        [string]$Directory,
        [string]$HealthUrl,
        [int]$TimeoutSeconds = 90
    )

    Write-Host "Starting $Name..." -ForegroundColor Green
    $process = Start-Process powershell -ArgumentList "cd $Directory; ./mvnw spring-boot:run" -PassThru

    Write-Host "Waiting for $Name to become healthy (timeout: ${TimeoutSeconds}s)..." -ForegroundColor Yellow
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($process.HasExited) {
            Write-Host "$Name process exited early (code $($process.ExitCode)) before becoming healthy." -ForegroundColor Red
            exit 1
        }
        try {
            $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($response.StatusCode -eq 200 -and ($response.Content | ConvertFrom-Json).status -eq "UP") {
                Write-Host "$Name is healthy." -ForegroundColor Green
                return $process
            }
        } catch {
            # Not ready yet - keep polling until the timeout elapses.
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "$Name did not become healthy within ${TimeoutSeconds}s." -ForegroundColor Red
    exit 1
}

Start-ServiceProcess -Name "Service Registry (Eureka)" -Directory "service-registry" -HealthUrl "http://localhost:8761/actuator/health" -TimeoutSeconds 60 | Out-Null
Start-ServiceProcess -Name "Config Server" -Directory "config-server" -HealthUrl "http://localhost:8888/actuator/health" -TimeoutSeconds 60 | Out-Null
Start-ServiceProcess -Name "API Gateway" -Directory "api-gateway" -HealthUrl "http://localhost:8765/actuator/health" -TimeoutSeconds 60 | Out-Null
Start-ServiceProcess -Name "Catalog Service" -Directory "catalog-service" -HealthUrl "http://localhost:18080/actuator/health" -TimeoutSeconds 60 | Out-Null
Start-ServiceProcess -Name "Inventory Service" -Directory "inventory-service" -HealthUrl "http://localhost:18181/inventory-service/actuator/health" -TimeoutSeconds 60 | Out-Null
Start-ServiceProcess -Name "Order Service" -Directory "order-service" -HealthUrl "http://localhost:18282/order-service/actuator/health" -TimeoutSeconds 60 | Out-Null
Start-ServiceProcess -Name "Payment Service" -Directory "payment-service" -HealthUrl "http://localhost:18085/payment-service/actuator/health" -TimeoutSeconds 60 | Out-Null

Write-Host "All backend services should now be running! (retail-store-webapp is not started by this script)" -ForegroundColor Green
Write-Host "You can access the Eureka dashboard at: http://localhost:8761" -ForegroundColor Cyan
Write-Host "You can test the API Gateway endpoint using: curl -X POST http://localhost:8765/api/v1/generate -H `"Idempotency-Key: test-123`"" -ForegroundColor Cyan

#!/usr/bin/env pwsh

param (
    [string]$TestProfile = "standard",
    [string]$BaseUrl = "http://localhost:8765",
    [int]$Users = 10,
    [int]$Duration = 300,
    [switch]$Help
)

# Display help information
if ($Help) {
    Write-Host "Gatling Performance Test Runner"
    Write-Host "==============================="
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\run-tests.ps1 [-TestProfile <profile>] [-BaseUrl <url>] [-Users <number>] [-Duration <seconds>] [-Help]"
    Write-Host ""
    Write-Host "Parameters:"
    Write-Host "  -TestProfile  Test profile to run (quick, standard, extended, resilience, stress, gateway, all)"
    Write-Host "  -BaseUrl      Base URL for the API Gateway (default: http://localhost:8765)"
    Write-Host "  -Users        Number of users for the test (default: 50)"
    Write-Host "  -Duration     Duration of the test in seconds (default: 300)"
    Write-Host "  -Help         Display this help message"
    Write-Host ""
    Write-Host "Profiles:"
    Write-Host "  quick      ~1-2 minutes, minimal load for smoke testing"
    Write-Host "  standard   ~3-5 minutes, balanced load for regular verification"
    Write-Host "  extended   ~10+ minutes, sustained load for stability testing"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\run-tests.ps1"
    Write-Host "  .\run-tests.ps1 -TestProfile quick"
    Write-Host "  .\run-tests.ps1 -TestProfile extended -Users 100"
    exit 0
}

function Check-Health {
    param ([string]$ServiceUrl)
    $MaxAttempts = 10
    $Attempt = 1
    $SleepTime = 5

    Write-Host "Checking health for $ServiceUrl..."
    while ($Attempt -le $MaxAttempts) {
        try {
            $Response = Invoke-RestMethod -Uri "$BaseUrl$ServiceUrl" -Method Get
            Write-Host "Service $ServiceUrl is UP!"
            return $true
        } catch {
            $Status = $_.Exception.Response.StatusCode.value__
            Write-Host "Attempt ${Attempt}/${MaxAttempts}: ${ServiceUrl} returned ${Status}. Retrying in ${SleepTime}s..."
            Start-Sleep -Seconds $SleepTime
            $Attempt++
        }
    }
    Write-Host "ERROR: Service $ServiceUrl failed to become healthy after $MaxAttempts attempts." -ForegroundColor Red
    return $false
}

Write-Host "Starting pre-flight health checks..."
$Services = @("/actuator/health", "/catalog-service/actuator/health", "/inventory-service/actuator/health", "/order-service/actuator/health", "/payment-service/actuator/health")

foreach ($Service in $Services) {
    if (-not (Check-Health -ServiceUrl $Service)) {
        Write-Host "Pre-flight checks failed. Aborting." -ForegroundColor Red
        exit 1
    }
}
Write-Host "All services are healthy. Proceeding with tests."

# Set Maven command based on the selected profile
switch ($TestProfile) {
    "quick" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=2 -DconstantUsers=5 -DrampDuration=15 -DtestDuration=60"
        Write-Host "Running quick test profile (1-2 min)..."
    }
    "standard" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=20 -DconstantUsers=$Users -DrampDuration=30 -DtestDuration=180"
        Write-Host "Running standard test profile (3-5 min)..."
    }
    "extended" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=50 -DconstantUsers=$Users -DrampDuration=60 -DtestDuration=600"
        Write-Host "Running extended test profile (10+ min)..."
    }
    "resilience" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DtargetRate=10 -P resilience"
        Write-Host "Running resilience test profile..."
    }
    "stress" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DmaxUsers=$Users -DplateauDurationMinutes=5 -P stress"
        Write-Host "Running stress test profile..."
    }
    "gateway" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DburstUsers=$Users -P gateway"
        Write-Host "Running API gateway resilience tests..."
    }
    "all" {
        $MavenParams = "-DbaseUrl=$BaseUrl -P all"
        Write-Host "Running all test simulations..."
    }
    default {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=10 -DconstantUsers=$Users -DrampDuration=30 -DtestDuration=120"
        Write-Host "Running default test profile..."
    }
}

# Run the tests
$cmd = ".\mvnw.cmd clean gatling:test $MavenParams"
Write-Host "Executing: $cmd"
Invoke-Expression $cmd

# Open the report after completion
$LatestReport = Get-ChildItem -Path ".\target\gatling\" -Directory | Sort-Object -Property LastWriteTime -Descending | Select-Object -First 1
if ($LatestReport) {
    $ReportPath = Join-Path -Path $LatestReport.FullName -ChildPath "index.html"
    if (Test-Path $ReportPath) {
        Write-Host "Test execution completed. Report available at: $ReportPath"
    }
} else {
    Write-Host "No test reports found."
}

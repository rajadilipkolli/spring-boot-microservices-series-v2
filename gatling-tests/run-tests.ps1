#!/usr/bin/env pwsh

param (
    [string]$TestProfile = "standard",
    [string]$BaseUrl = "http://localhost:8765",
    [int]$Users = 50,
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

function Test-ServiceHealth {
    param ([string]$ServiceUrl)
    $MaxAttempts = 10
    $Attempt = 1
    $SleepTime = 5

    Write-Host "Checking health for $ServiceUrl..."
    while ($Attempt -le $MaxAttempts) {
        try {
            [void](Invoke-RestMethod -Uri "$BaseUrl$ServiceUrl" -Method Get)
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
    if (-not (Test-ServiceHealth -ServiceUrl $Service)) {
        Write-Host "Pre-flight checks failed. Aborting." -ForegroundColor Red
        exit 1
    }
}
Write-Host "All services are healthy. Proceeding with tests."

# Set Maven command based on the selected profile
switch ($TestProfile) {
    "quick" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=2 -DconstantUsers=$Users -DrampDuration=15 -DtestDuration=$Duration"
        Write-Host "Running quick test profile (using users=$Users, duration=${Duration}s)..."
    }
    "standard" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=20 -DconstantUsers=$Users -DrampDuration=30 -DtestDuration=$Duration"
        Write-Host "Running standard test profile (using users=$Users, duration=${Duration}s)..."
    }
    "extended" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=50 -DconstantUsers=$Users -DrampDuration=60 -DtestDuration=$Duration"
        Write-Host "Running extended test profile (using users=$Users, duration=${Duration}s)..."
    }
    "resilience" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DconstantUsers=$Users -DtestDuration=$Duration -P resilience"
        Write-Host "Running resilience test profile (using users=$Users, duration=${Duration}s)..."
    }
    "stress" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DconstantUsers=$Users -DtestDuration=$Duration -P stress"
        Write-Host "Running stress test profile (using users=$Users, duration=${Duration}s)..."
    }
    "gateway" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DburstUsersPerSec=$Users -DtestDuration=$Duration -P gateway"
        Write-Host "Running API gateway resilience tests (using burstUsersPerSec=$Users, duration=${Duration}s)..."
    }
    "all" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DconstantUsers=$Users -DtestDuration=$Duration -P all"
        Write-Host "Running all test simulations (using users=$Users, duration=${Duration}s)..."
    }
    default {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=10 -DconstantUsers=$Users -DrampDuration=30 -DtestDuration=$Duration"
        Write-Host "Running default test profile (using users=$Users, duration=${Duration}s)..."
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

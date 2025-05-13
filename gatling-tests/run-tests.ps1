#!/usr/bin/env pwsh

param (
    [string]$TestProfile = "default",
    [string]$BaseUrl = "http://localhost:8765",
    [int]$Users = 10,
    [int]$Duration = 60,
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
    Write-Host "  -TestProfile  Test profile to run (default, health-check, quick, heavy, resilience, stress, gateway, all)"
    Write-Host "  -BaseUrl      Base URL for the API Gateway (default: http://localhost:8765)"
    Write-Host "  -Users        Number of users for the test (default: 10)"
    Write-Host "  -Duration     Duration of the test in seconds (default: 60)"
    Write-Host "  -Help         Display this help message"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\run-tests.ps1"
    Write-Host "  .\run-tests.ps1 -TestProfile quick"
    Write-Host "  .\run-tests.ps1 -TestProfile heavy -Users 50 -Duration 300"
    exit 0
}

# Set Maven command based on the selected profile
switch ($TestProfile) {
    "health-check" {
        $MavenParams = "-DbaseUrl=$BaseUrl -Dgatling.simulations=simulation.ServiceHealthCheckSimulation"
        Write-Host "Running health check simulation to verify all services are up..."
    }
    "quick" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=2 -DconstantUsers=5 -DrampDuration=10 -DtestDuration=30"
        Write-Host "Running quick test profile with minimal load..."
    }
    "heavy" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=20 -DconstantUsers=$Users -DrampDuration=30 -DtestDuration=$Duration"
        Write-Host "Running heavy test profile with high load..."
    }
    "resilience" {
        $MavenParams = "-DbaseUrl=$BaseUrl -Dusers=$Users -DtestDuration=$Duration -P resilience"
        Write-Host "Running resilience test profile to check error handling..."
    }
    "stress" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DmaxUsers=$Users -DrampDurationMinutes=2 -DplateauDurationMinutes=$([Math]::Max(1, $Duration / 60)) -P stress"
        Write-Host "Running stress test profile with increasing load..."
    }
    "gateway" {
        $MavenParams = "-DbaseUrl=$BaseUrl -DburstUsers=$Users -DsustainSeconds=$Duration -P gateway"
        Write-Host "Running API gateway resilience tests..."
    }
    "all" {
        $MavenParams = "-DbaseUrl=$BaseUrl -P all"
        Write-Host "Running all test simulations..."
    }
    default {
        $MavenParams = "-DbaseUrl=$BaseUrl -DrampUsers=5 -DconstantUsers=$Users -DrampDuration=15 -DtestDuration=$Duration"
        Write-Host "Running default test profile..."
    }
}

# Run the tests
$cmd = "mvn clean gatling:test $MavenParams"
Write-Host "Executing: $cmd"
Invoke-Expression $cmd

# Open the report after completion
$LatestReport = Get-ChildItem -Path ".\target\gatling\" -Directory | Sort-Object -Property LastWriteTime -Descending | Select-Object -First 1
if ($LatestReport) {
    $ReportPath = Join-Path -Path $LatestReport.FullName -ChildPath "index.html"
    if (Test-Path $ReportPath) {
        Write-Host "Opening test report at: $ReportPath"
        Start-Process $ReportPath
    } else {
        Write-Host "Report not found at: $ReportPath"
    }
} else {
    Write-Host "No test reports found."
}

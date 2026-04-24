@echo off
echo Running Gatling performance tests with optimized configuration...

:: Change to script directory (drive- and space-safe)
cd /d "%~dp0"

:: Run Maven and propagate failure to caller
:: Using consistent parameter names matching the updated simulation logic
call mvn clean gatling:test ^
  -Dgatling.simulationClass=simulation.CreateProductSimulation ^
  -DrampUsers=50 ^
  -DconstantUsers=100 ^
  -DrampDuration=60 ^
  -DtestDuration=300 || exit /b %ERRORLEVEL%

echo Test execution completed. Check reports in target/gatling folder.

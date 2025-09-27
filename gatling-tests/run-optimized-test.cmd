@echo off
echo Running Gatling performance tests with optimized configuration...

cd %~dp0
call mvn clean gatling:test ^
  -Dgatling.simulationClass=simulation.CreateProductSimulation ^
  -DrampUsers=50 ^
  -DconstantUsers=100 ^
  -DrampDuration=30 ^
  -DtestDuration=180 ^
  -DkafkaInitDelay=10

echo Test execution completed. Check reports in target/gatling folder.

package simulation;

import static config.Configuration.*;
import static data.Feeders.*;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple simulation to test that health checks are working properly. This simulation doesn't
 * generate any load - it just verifies that all services are up.
 */
public class ServiceHealthCheckSimulation extends BaseLoadSimulation {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ServiceHealthCheckSimulation.class);

    // Health checks are performed by run-tests.sh before this simulation is invoked.
    // This scenario simply verifies the Gatling engine can connect and records a single
    // successful session — use it as a connectivity smoke test only.
    ScenarioBuilder healthCheckScenario =
            scenario("Health Check Scenario")
                    .exec(
                            http("Gateway Health Check")
                                    .get("/actuator/health")
                                    .check(status().is(200)))
                    .exec(
                            session -> {
                                LOGGER.info("Health checks passed successfully!");
                                return session;
                            });

    // Constructor that will trigger the health checks
    public ServiceHealthCheckSimulation() {
        // Set up the simulation with minimal load since we're just testing health checks
        setUp(healthCheckScenario.injectOpen(atOnceUsers(1)));
    }
}

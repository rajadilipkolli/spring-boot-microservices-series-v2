package simulation;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.ScenarioBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple simulation to test that health checks are working properly. This simulation doesn't
 * generate any load - it just verifies that all services are up.
 */
public class ServiceHealthCheckSimulation extends BaseSimulation {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ServiceHealthCheckSimulation.class);

    // Define a simple scenario that does nothing - the BaseSimulation.runHealthChecks()
    // will handle the service health validation
    ScenarioBuilder healthCheckScenario =
            scenario("Health Check Scenario")
                    .exec(
                            session -> {
                                LOGGER.info("Health checks passed successfully!");
                                return session;
                            });

    // Constructor that will trigger the health checks
    public ServiceHealthCheckSimulation() {
        // Run the health checks first - BaseSimulation will throw an exception if services aren't
        // ready
        runHealthChecks();

        // Set up the simulation with minimal load since we're just testing health checks
        setUp(healthCheckScenario.injectOpen(atOnceUsers(1)));
    }
}

package simulation;

import static config.Configuration.HTTP_PROTOCOL;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

public abstract class BaseLoadSimulation extends Simulation {

    protected void setUpSimulation(
            Duration maxDuration, Assertion[] assertions, PopulationBuilder... populationBuilders) {
        setUp(populationBuilders)
                .protocols(HTTP_PROTOCOL)
                .maxDuration(maxDuration)
                .assertions(assertions);
    }
}

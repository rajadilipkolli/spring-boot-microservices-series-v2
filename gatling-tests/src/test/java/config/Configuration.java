package config;

import static io.gatling.javaapi.http.HttpDsl.http;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;

public class Configuration {
    private static final Config config = ConfigFactory.load();

    public static final String BASE_URL = config.getString("simulation.baseUrl");
    public static final int RAMP_USERS = config.getInt("simulation.load.rampUsers");
    public static final int CONSTANT_USERS = config.getInt("simulation.load.constantUsers");
    public static final int RAMP_DURATION_SECONDS = config.getInt("simulation.load.rampDuration");
    public static final int TEST_DURATION_SECONDS = config.getInt("simulation.load.testDuration");
    public static final int BURST_USERS_PER_SEC = config.getInt("simulation.load.burstUsersPerSec");

    public static final HttpProtocolBuilder HTTP_PROTOCOL =
            http.baseUrl(BASE_URL)
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .userAgentHeader("Gatling/Performance Test")
                    .disableCaching()
                    .shareConnections()
                    .maxConnectionsPerHost(100)
                    .connectionHeader("keep-alive")
                    .acceptEncodingHeader("gzip, deflate")
                    .enableHttp2()
                    .header(
                            "traceparent",
                            session -> {
                                String traceId = UUID.randomUUID().toString().replace("-", "");
                                return "00-" + traceId + "-0000000000000000-01";
                            });
}

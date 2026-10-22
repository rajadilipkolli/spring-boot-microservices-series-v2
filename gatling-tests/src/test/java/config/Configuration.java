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

    // SLAs
    public static final int SLA_MEAN_MS = config.getInt("simulation.sla.meanMs");
    public static final int SLA_P95_MS = config.getInt("simulation.sla.p95Ms");
    public static final int SLA_P99_MS = config.getInt("simulation.sla.p99Ms");
    public static final int SLA_STRESS_MEAN_MS = config.getInt("simulation.sla.stressMeanMs");
    public static final int SLA_STRESS_P95_MS = config.getInt("simulation.sla.stressP95Ms");
    public static final int SLA_STRESS_P99_MS = config.getInt("simulation.sla.stressP99Ms");
    public static final int SLA_RESILIENCE_P95_MS = config.getInt("simulation.sla.resilienceP95Ms");
    public static final int SLA_GATEWAY_P99_MS = config.getInt("simulation.sla.gatewayP99Ms");
    public static final double SLA_MAX_ERROR_PERCENT =
            config.getDouble("simulation.sla.maxErrorPercent");
    public static final double SLA_MIN_SUCCESS_PERCENT =
            config.getDouble("simulation.sla.minSuccessPercent");

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
                                String parentId =
                                        UUID.randomUUID()
                                                .toString()
                                                .replace("-", "")
                                                .substring(0, 16);
                                return "00-" + traceId + "-" + parentId + "-01";
                            });
}

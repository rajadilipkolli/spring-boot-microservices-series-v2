package config;

import static io.gatling.javaapi.http.HttpDsl.http;

import io.gatling.javaapi.http.HttpProtocolBuilder;

public class Configuration {
    public static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8765");

    public static final int RAMP_USERS = Integer.parseInt(System.getProperty("rampUsers", "5"));
    public static final int CONSTANT_USERS =
            Integer.parseInt(System.getProperty("constantUsers", "10"));
    public static final int RAMP_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("rampDuration", "30"));
    public static final int TEST_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("testDuration", "60"));
    public static final int BURST_USERS_PER_SEC =
            Integer.parseInt(System.getProperty("burstUsersPerSec", "30"));

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
                    .enableHttp2();
}

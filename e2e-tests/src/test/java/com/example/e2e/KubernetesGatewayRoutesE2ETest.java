package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import com.example.e2e.config.E2EConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
@DisplayName("E2E - Kubernetes Gateway Routes Checks")
class KubernetesGatewayRoutesE2ETest {

    @Test
    @DisplayName("GET / on webapp host should return 200 or redirect")
    void webappRootShouldReturnSuccessOrRedirect() {
        String webappUrl = E2EConfig.getWebappUrl();
        Assumptions.assumeTrue(
                webappUrl != null && !webappUrl.isBlank(),
                "Webapp URL is not configured; skipping webapp gateway route test.");

        given().redirects()
                .follow(false)
                .when()
                .get(webappUrl + "/")
                .then()
                .statusCode(anyOf(is(200), is(301), is(302), is(303), is(307), is(308)));
    }

    @Test
    @DisplayName("GET / on JobRunr host should return 401 without credentials")
    void jobrunrRootShouldRequireAuthentication() {
        String jobrunrUrl = E2EConfig.getJobrunrUrl();
        Assumptions.assumeTrue(
                jobrunrUrl != null && !jobrunrUrl.isBlank(),
                "JobRunr URL is not configured; skipping JobRunr route test.");

        given().redirects().follow(false).when().get(jobrunrUrl + "/").then().statusCode(401);
    }

    @Test
    @DisplayName("GET / on JobRunr host should return 200 with configured credentials")
    void jobrunrRootShouldSucceedWithCredentials() {
        String jobrunrUrl = E2EConfig.getJobrunrUrl();
        Assumptions.assumeTrue(
                jobrunrUrl != null && !jobrunrUrl.isBlank(),
                "JobRunr URL is not configured; skipping JobRunr route test.");

        String username = E2EConfig.getJobrunrUsername();
        String password = E2EConfig.getJobrunrPassword();
        Assumptions.assumeTrue(
                username != null && !username.isBlank() && password != null && !password.isBlank(),
                "JobRunr credentials are not configured; skipping authenticated JobRunr test.");

        given().auth()
                .preemptive()
                .basic(username, password)
                .when()
                .get(jobrunrUrl + "/")
                .then()
                .statusCode(200);
    }
}

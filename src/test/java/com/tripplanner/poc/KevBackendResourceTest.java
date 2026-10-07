package com.tripplanner.poc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(KevBackendResourceTest.KevProfile.class)
class KevBackendResourceTest {

    @Test
    void selectsTheNamedKevDecisionModel() {
        given()
                .when().get("/trip/backend")
                .then()
                .statusCode(200)
                .body("backend", equalTo("kev"))
                .body("model", equalTo("kev-latest"));
    }

    public static class KevProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("decision.backend", "kev");
        }
    }
}

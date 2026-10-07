package com.tripplanner.poc;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class BackendResourceTest {

    @Test
    void startsWithTheOfflineDecisionBackend() {
        given()
                .when().get("/trip/backend")
                .then()
                .statusCode(200)
                .body("backend", equalTo("stub"))
                .body("model", equalTo("stub"));
    }
}

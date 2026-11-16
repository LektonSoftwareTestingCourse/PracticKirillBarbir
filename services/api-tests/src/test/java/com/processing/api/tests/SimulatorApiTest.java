package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@DisplayName("6. Simulator Endpoint Tests")
public class SimulatorApiTest extends BaseApiTest {

    @Test
    @DisplayName("POST /api/simulator/terminal/run returns 200 with totalSubmitted, approved, declined counts")
    void testTerminalSimulatorRunReturns200() {
        Map<String, Object> body = Map.of(
                "count", 10,
                "scenario", "normal",
                "tps", 10
        );

        Response response = given()
                .spec(gatewaySpec)
                .body(body)
                .when()
                .post("/api/simulator/terminal/run");

        response.then()
                .statusCode(200)
                .body("totalSubmitted", equalTo(10))
                .body("approved", greaterThanOrEqualTo(0))
                .body("declined", greaterThanOrEqualTo(0))
                .body("elapsedMs", greaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("POST /api/simulator/merchant/run returns 200 and simulated stats")
    void testMerchantSimulatorRunReturns200() {
        Map<String, Object> body = Map.of(
                "count", 5,
                "scenario", "grocery"
        );

        Response response = given()
                .spec(gatewaySpec)
                .body(body)
                .when()
                .post("/api/simulator/merchant/run");

        response.then()
                .statusCode(200)
                .body("totalSubmitted", equalTo(5))
                .body("elapsedMs", greaterThanOrEqualTo(0));
    }
}

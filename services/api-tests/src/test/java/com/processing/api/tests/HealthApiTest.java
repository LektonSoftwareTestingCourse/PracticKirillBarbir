package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import com.processing.api.support.JsonSchemaValidator;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("1. Health Endpoint Tests")
public class HealthApiTest extends BaseApiTest {

    @Test
    @DisplayName("GET /health on Gateway returns 200, schema match and ok status for all downstream services")
    void testGatewayHealthReturns200AndValidContract() {
        RequestSpecification spec = gatewaySpec;

        Response response = given()
                .spec(spec)
                .when()
                .get("/health");

        response.then()
                .statusCode(200)
                .header("Content-Type", startsWith("application/json"))
                .body("status", equalTo("ok"))
                .body("service", equalTo("gateway"))
                .body("version", notNullValue())
                .body("services.switch", equalTo("ok"))
                .body("services.authorization", equalTo("ok"))
                .body("services.cardManagement", equalTo("ok"))
                .body("services.logger", equalTo("ok"));

        JsonSchemaValidator.assertHealthResponseSchema(response.asString());
    }

    @ParameterizedTest(name = "GET /health directly on service: {0}")
    @ValueSource(strings = {"card-management", "switch", "authorization", "transaction-logger",
            "terminal-simulator", "merchant-acquirer"})
    @DisplayName("GET /health on internal downstream microservices returns 200 and valid status")
    void testDownstreamServicesHealth(String serviceName) {
        RequestSpecification spec = switch (serviceName) {
            case "card-management" -> cardMgmtSpec;
            case "switch" -> switchSpec;
            case "authorization" -> authSpec;
            case "transaction-logger" -> loggerSpec;
            case "terminal-simulator" -> terminalSpec;
            case "merchant-acquirer" -> merchantSpec;
            default -> gatewaySpec;
        };

        Response response = given()
                .spec(spec)
                .when()
                .get("/health");

        response.then()
                .statusCode(200)
                .header("Content-Type", startsWith("application/json"))
                .body("status", equalTo("ok"))
                .body("service", anyOf(equalTo(serviceName), equalTo(serviceName + "-simulator")));
    }
}

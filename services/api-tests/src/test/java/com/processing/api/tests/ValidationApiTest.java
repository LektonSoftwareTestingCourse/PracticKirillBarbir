package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import com.processing.api.support.JsonSchemaValidator;
import com.processing.api.support.TransactionRequestBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@DisplayName("4. Request Validation & Equivalence Classes Tests")
public class ValidationApiTest extends BaseApiTest {

    @ParameterizedTest(name = "Invalid PAN: {0}")
    @ValueSource(strings = {"12345", "400000ABC4567890"})
    @DisplayName("PAN must be exactly 16 numeric digits, violating values rejected with 400")
    void testInvalidPanRejection(String invalidPan) {
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(invalidPan)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("pan"))
                .body("serviceName", equalTo("gateway"));
    }

    @ParameterizedTest(name = "Non-positive Amount: {0}")
    @ValueSource(ints = {0, -500})
    @DisplayName("Amount must be positive integer (> 0), 0 and negative rejected with 400")
    void testNonPositiveAmountRejection(int nonPositiveAmount) {
        String pan = createDefaultActiveCard();
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(nonPositiveAmount)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("amount"))
                .body("serviceName", equalTo("gateway"));
    }

    @Test
    @DisplayName("Missing mandatory field pan rejected with 400 and validation message")
    void testMissingMandatoryFieldPan() {
        Map<String, Object> request = TransactionRequestBuilder.aTransaction().build();
        request.remove("pan");

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("pan"))
                .body("serviceName", equalTo("gateway"));

        JsonSchemaValidator.assertErrorResponseSchema(response.asString());
    }

    @Test
    @DisplayName("Malformed JSON payload rejected with 400 Bad Request")
    void testMalformedJsonPayloadReturns400() {
        given()
                .spec(gatewaySpec)
                .body("{ \"mti\": \"0100\", \"brokenJson\" ")
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(400);
    }
}

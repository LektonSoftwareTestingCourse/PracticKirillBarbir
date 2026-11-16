package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import com.processing.api.support.DataGenerator;
import com.processing.api.support.InternalTransactionRequestBuilder;
import com.processing.api.support.TransactionRequestBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("8. Internal Endpoints & Redelivery Tests")
public class InternalServicesApiTest extends BaseApiTest {

    @Test
    @DisplayName("POST /api/internal/route on Switch routes transaction and returns 200 AuthorizationResponse")
    void testDirectSwitchRouteReturns200() {
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan("4000001234560001")
                .build();

        Response response = given()
                .spec(switchSpec)
                .body(request)
                .when()
                .post("/api/internal/route");

        response.then()
                .statusCode(200)
                .body("mti", anyOf(equalTo("0110"), equalTo("0100")))
                .body("responseCode", notNullValue());
    }

    @Test
    @DisplayName("POST /api/internal/authorize on Authorization service returns 404 when card is not found")
    void testDirectAuthorizeCardNotFoundReturns404() {
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan("4000009999999999")
                .withIssuerId("ISS001")
                .build();

        Response response = given()
                .spec(authSpec)
                .body(request)
                .when()
                .post("/api/internal/authorize");

        response.then()
                .statusCode(404)
                .body("declineReason", equalTo("CARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/internal/rollback returns 200 on initial rollback")
    void testRollbackSuccess() {
        String pan = createDefaultActiveCard();
        Map<String, Object> tx = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(1000)
                .build();

        Response authResp = given()
                .spec(gatewaySpec)
                .body(tx)
                .when()
                .post("/api/transactions");

        authResp.then().statusCode(200);
        String rrn = authResp.then().extract().path("rrn");

        Map<String, Object> rollbackReq = Map.of(
                "rrn", rrn,
                "pan", pan,
                "amount", 1000
        );

        given()
                .spec(authSpec)
                .body(rollbackReq)
                .when()
                .post("/api/internal/rollback")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Logger idempotency: 201 Created on first arrival, 200 OK on duplicate, 409 Conflict on altered data")
    void testLoggerIdempotencyAndDuplicateRedelivery() {
        String txId = DataGenerator.generateUuid();
        String rrn = DataGenerator.generateRrn();
        Map<String, Object> tx = InternalTransactionRequestBuilder.aTransaction()
                .withId(txId)
                .withRrn(rrn)
                .withAmount(new BigDecimal("250000"))
                .build();

        given()
                .spec(loggerSpec)
                .body(tx)
                .when()
                .post("/api/internal/log")
                .then()
                .statusCode(201);

        given()
                .spec(loggerSpec)
                .body(tx)
                .when()
                .post("/api/internal/log")
                .then()
                .statusCode(200);

        Map<String, Object> conflictingTx = InternalTransactionRequestBuilder.aTransaction()
                .withId(txId)
                .withRrn(rrn)
                .withAmount(new BigDecimal("999999"))
                .build();

        given()
                .spec(loggerSpec)
                .body(conflictingTx)
                .when()
                .post("/api/internal/log")
                .then()
                .statusCode(409);
    }
}

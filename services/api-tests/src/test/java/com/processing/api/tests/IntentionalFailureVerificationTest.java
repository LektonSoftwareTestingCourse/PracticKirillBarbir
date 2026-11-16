package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import com.processing.api.support.DataGenerator;
import com.processing.api.support.InternalTransactionRequestBuilder;
import com.processing.api.support.TransactionRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("11. Intentional Breakage & Mutation Proof Tests")
public class IntentionalFailureVerificationTest extends BaseApiTest {

    @Test
    @DisplayName("Proof 1: Expecting status 500 on valid GET /health fails assertion")
    void testIntentionalBreakageOnHealthStatusCode() {
        assertThrows(AssertionError.class, () -> {
            given()
                    .spec(gatewaySpec)
                    .when()
                    .get("/health")
                    .then()
                    .statusCode(500);
        });
    }

    @Test
    @DisplayName("Proof 2: Expecting DECLINED on valid funded transaction fails assertion")
    void testIntentionalBreakageOnApprovalStatus() {
        String pan = createDefaultActiveCard();
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(10000)
                .build();

        assertThrows(AssertionError.class, () -> {
            given()
                    .spec(gatewaySpec)
                    .body(request)
                    .when()
                    .post("/api/transactions")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("DECLINED"));
        });
    }

    @Test
    @DisplayName("Proof 3: Expecting RRN pattern of 5 digits fails assertion against 12-digit contract")
    void testIntentionalBreakageOnRrnPattern() {
        String pan = createDefaultActiveCard();
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .build();

        assertThrows(AssertionError.class, () -> {
            given()
                    .spec(gatewaySpec)
                    .body(request)
                    .when()
                    .post("/api/transactions")
                    .then()
                    .statusCode(200)
                    .body("rrn", matchesPattern("^\\d{5}$"));
        });
    }

    @Test
    @DisplayName("Mutation Proof: Mutating idempotency payload causes 200 expectation to fail with 409")
    void testMutationDeduplicationCheckTurnsRed() {
        String txId = DataGenerator.generateUuid();
        Map<String, Object> initialTx = InternalTransactionRequestBuilder.aTransaction()
                .withId(txId)
                .withAmount(new BigDecimal("100000"))
                .build();

        given()
                .spec(loggerSpec)
                .body(initialTx)
                .when()
                .post("/api/internal/log")
                .then()
                .statusCode(201);

        assertThrows(AssertionError.class, () -> {
            Map<String, Object> mutatedTx = InternalTransactionRequestBuilder.aTransaction()
                    .withId(txId)
                    .withAmount(new BigDecimal("999999"))
                    .build();

            given()
                    .spec(loggerSpec)
                    .body(mutatedTx)
                    .when()
                    .post("/api/internal/log")
                    .then()
                    .statusCode(200);
        });
    }
}

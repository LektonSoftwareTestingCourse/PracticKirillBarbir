package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import com.processing.api.support.DataGenerator;
import com.processing.api.support.JsonSchemaValidator;
import com.processing.api.support.TransactionRequestBuilder;
import com.processing.api.support.mock.SmpMockServer;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("3. Transaction Authorization & Business Declines Tests")
public class TransactionAuthorizationApiTest extends BaseApiTest {

    @Test
    @DisplayName("POST /api/transactions approved: returns 200, status APPROVED, responseCode 00 and generated RRN")
    void testSuccessfulAuthorizationReturns200ApprovedAndValidContract() {
        String pan = createCardViaApi(new BigDecimal("5000000"), new BigDecimal("10000000"));
        String stan = DataGenerator.generateStan();
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withStan(stan)
                .withAmount(150000)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("mti", anyOf(equalTo("0110"), equalTo("0100")))
                .body("stan", equalTo(stan))
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"))
                .body("declineReason", nullValue())
                .body("rrn", matchesPattern("^\\d{12}$"))
                .body("authCode", matchesPattern("^[A-Z0-9]{6}$"))
                .body("processingTimeMs", greaterThanOrEqualTo(0));

        JsonSchemaValidator.assertAuthorizationResponseSchema(response.asString());
    }

    @Test
    @DisplayName("Decline 51: INSUFFICIENT_FUNDS when request amount exceeds card available balance")
    void testDeclineInsufficientFunds() {
        String pan = createCardViaApi(new BigDecimal("1000"), new BigDecimal("10000000"));
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(50000)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("51"))
                .body("declineReason", equalTo("INSUFFICIENT_FUNDS"))
                .body("rrn", nullValue())
                .body("authCode", nullValue());
    }

    @Test
    @DisplayName("Decline 61: EXCEEDS_AMOUNT_LIMIT when request amount exceeds configured daily limit")
    void testDeclineExceedsDailyLimit() {
        String pan = createCardViaApi(new BigDecimal("10000000"), new BigDecimal("5000"));
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(25000)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("61"))
                .body("declineReason", equalTo("EXCEEDS_AMOUNT_LIMIT"));
    }

    @Test
    @DisplayName("Decline 05: CARD_BLOCKED when card status is set to BLOCKED")
    void testDeclineCardBlocked() {
        String pan = createDefaultActiveCard();

        given()
                .spec(cardMgmtSpec)
                .body(Map.of("status", "BLOCKED"))
                .when()
                .patch("/api/cards/" + pan)
                .then()
                .statusCode(200);

        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(1000)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("05"))
                .body("declineReason", equalTo("CARD_BLOCKED"));
    }

    @Test
    @DisplayName("Decline 05: CARD_INACTIVE when card status is set to INACTIVE")
    void testDeclineCardInactive() {
        String pan = createDefaultActiveCard();

        given()
                .spec(cardMgmtSpec)
                .body(Map.of("status", "INACTIVE"))
                .when()
                .patch("/api/cards/" + pan)
                .then()
                .statusCode(200);

        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(1000)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("05"))
                .body("declineReason", equalTo("CARD_INACTIVE"));
    }

    @Test
    @DisplayName("Decline 54: CARD_EXPIRED when card status is set to EXPIRED")
    void testDeclineCardExpiredByStatus() {
        String pan = createDefaultActiveCard();

        given()
                .spec(cardMgmtSpec)
                .body(Map.of("status", "EXPIRED"))
                .when()
                .patch("/api/cards/" + pan)
                .then()
                .statusCode(200);

        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(1000)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("54"))
                .body("declineReason", equalTo("CARD_EXPIRED"));
    }

    @Test
    @DisplayName("Decline 54: CARD_EXPIRED via request transmissionDateTime later than card expiry date")
    void testDeclineCardExpiredByTransmissionDate() {
        String pan = createDefaultActiveCard();

        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withTransmissionDateTime("2099-01-01T00:00:00Z")
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("54"))
                .body("declineReason", equalTo("CARD_EXPIRED"));
    }

    @Test
    @DisplayName("Decline 14: CARD_NOT_FOUND when PAN does not exist in CMS")
    void testDeclineCardNotFoundByPan() {
        String nonExistentPan = "4000009999999995";
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(nonExistentPan)
                .withAmount(10000)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("14"))
                .body("declineReason", equalTo("CARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("Decline 14: CARD_NOT_FOUND via unknown BIN rejected by Switch routing")
    void testDeclineCardNotFoundByUnknownBin() {
        String unknownBinPan = "9999991234567890";
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(unknownBinPan)
                .withAmount(10000)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions");

        response.then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("14"))
                .body("declineReason", equalTo("CARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("Rate Limiter returns 429 and Retry-After header upon request threshold overflow")
    void testRateLimitingReturns429AndRetryAfterHeader() throws Exception {
        String pan = createDefaultActiveCard();
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .build();

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(com.processing.api.support.TestConfig.getGatewayUrl() + "/api/transactions"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Forwarded-For", "192.0.2.1")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();

        java.util.List<java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<String>>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 130; i++) {
            futures.add(client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofString()));
        }

        boolean received429 = false;
        for (java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<String>> future : futures) {
            try {
                java.net.http.HttpResponse<String> resp = future.join();
                if (resp.statusCode() == 429) {
                    received429 = true;
                    org.junit.jupiter.api.Assertions.assertTrue(resp.headers().firstValue("Retry-After").isPresent());
                    org.junit.jupiter.api.Assertions.assertTrue(resp.body().contains("RATE_LIMIT_EXCEEDED"));
                }
            } catch (Exception ignored) {
            }
        }

        SmpMockServer.resetRateLimit();
        org.junit.jupiter.api.Assertions.assertTrue(received429,
                "Expected HTTP 429 with Retry-After header during high burst rate");
    }
}

package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import com.processing.api.support.CardRequestBuilder;
import com.processing.api.support.JsonSchemaValidator;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("2. Card Management Endpoint Tests")
public class CardManagementApiTest extends BaseApiTest {

    @Test
    @DisplayName("POST /api/cards creates card, returns 201 and full Card model")
    void testCreateCardReturns201AndValidContract() {
        Map<String, Object> body = CardRequestBuilder.aCard()
                .withBin("400000")
                .withCardholderName("PETR PETROV")
                .withCurrencyCode("643")
                .withDailyLimit(new BigDecimal("15000000"))
                .withMonthlyLimit(new BigDecimal("300000000"))
                .withInitialBalance(new BigDecimal("100000000"))
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(body)
                .when()
                .post("/api/cards");

        response.then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("pan", matchesPattern("^400000\\d{10}$"))
                .body("bin", equalTo("400000"))
                .body("cardholderName", equalTo("PETR PETROV"))
                .body("expiryDate", matchesPattern("^(\\d{4}|\\d{4}-\\d{2})$"))
                .body("status", equalTo("ACTIVE"))
                .body("currencyCode", equalTo("643"))
                .body("availableBalance", equalTo(100000000))
                .body("issuerId", equalTo("ISS001"))
                .body("createdAt", notNullValue());

        JsonSchemaValidator.assertCardSchema(response.asString());
    }

    @Test
    @DisplayName("GET /api/cards/{pan} returns 200 and matches created card details")
    void testGetCardByPanReturns200AndValidDetails() {
        String pan = createCardViaApi(new BigDecimal("5000000"), new BigDecimal("10000000"));

        Response response = given()
                .spec(gatewaySpec)
                .when()
                .get("/api/cards/" + pan);

        response.then()
                .statusCode(200)
                .body("pan", equalTo(pan))
                .body("status", equalTo("ACTIVE"))
                .body("availableBalance", equalTo(5000000))
                .body("dailyLimit", equalTo(10000000));
    }

    @Test
    @DisplayName("GET /api/cards/{pan} returns X-Cache: MISS on first request and X-Cache: HIT on subsequent request")
    void testCardCachingHeaderMissThenHit() {
        String pan = createDefaultActiveCard();

        given()
                .spec(gatewaySpec)
                .when()
                .get("/api/cards/" + pan)
                .then()
                .statusCode(200)
                .header("X-Cache", equalTo("MISS"));

        given()
                .spec(gatewaySpec)
                .when()
                .get("/api/cards/" + pan)
                .then()
                .statusCode(200)
                .header("X-Cache", equalTo("HIT"));
    }

    @Test
    @DisplayName("GET /api/cards returns list and GPath filters cards by status")
    void testListCardsWithGPathFilter() {
        createDefaultActiveCard();

        Response response = given()
                .spec(gatewaySpec)
                .queryParam("status", "ACTIVE")
                .when()
                .get("/api/cards");

        response.then()
                .statusCode(200)
                .body("cards", notNullValue())
                .body("cards.findAll { it.status == 'ACTIVE' }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("GET /api/cards/{pan} with non-existent PAN returns 404 Not Found")
    void testGetNonExistentCardReturns404() {
        given()
                .spec(gatewaySpec)
                .when()
                .get("/api/cards/4000009999999999")
                .then()
                .statusCode(404)
                .body("error", anyOf(equalTo("NOT_FOUND"), equalTo("CardNotFoundException")));
    }
}

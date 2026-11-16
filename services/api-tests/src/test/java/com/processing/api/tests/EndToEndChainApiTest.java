package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import com.processing.api.support.CardRequestBuilder;
import com.processing.api.support.DataGenerator;
import com.processing.api.support.TransactionRequestBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

@DisplayName("10. End-to-End Processing Chain Test")
public class EndToEndChainApiTest extends BaseApiTest {

    @Test
    @DisplayName("Full E2E Chain: Card creation -> Authorization -> Balance deduction -> Logger storage -> Search")
    void testCompleteProcessingChain() {
        String cardholder = "END TO END TESTER";
        Map<String, Object> cardRequest = CardRequestBuilder.aCard()
                .withCardholderName(cardholder)
                .withInitialBalance(new BigDecimal("100000"))
                .withDailyLimit(new BigDecimal("15000000"))
                .build();

        Response cardResponse = given()
                .spec(gatewaySpec)
                .body(cardRequest)
                .when()
                .post("/api/cards");

        cardResponse.then()
                .statusCode(201)
                .body("availableBalance", equalTo(100000))
                .body("status", equalTo("ACTIVE"));

        String pan = cardResponse.then().extract().path("pan");

        given()
                .spec(gatewaySpec)
                .when()
                .get("/api/cards/" + pan)
                .then()
                .statusCode(200)
                .body("cardholderName", equalTo(cardholder))
                .body("availableBalance", equalTo(100000));

        String stan = DataGenerator.generateStan();
        Map<String, Object> txRequest = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withStan(stan)
                .withAmount(35000)
                .build();

        Response authResponse = given()
                .spec(gatewaySpec)
                .body(txRequest)
                .when()
                .post("/api/transactions");

        authResponse.then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"))
                .body("declineReason", nullValue())
                .body("rrn", matchesPattern("^\\d{12}$"))
                .body("authCode", notNullValue());

        String rrn = authResponse.then().extract().path("rrn");

        given()
                .spec(cardMgmtSpec)
                .when()
                .get("/api/cards/" + pan)
                .then()
                .statusCode(200)
                .body("availableBalance", equalTo(65000));

        await().atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    given()
                            .spec(gatewaySpec)
                            .queryParam("pan", pan)
                            .when()
                            .get("/api/transactions/search")
                            .then()
                            .statusCode(200)
                            .body("total", greaterThanOrEqualTo(1))
                            .body("transactions.findAll { it.rrn == '" + rrn + "' }.size()", equalTo(1));
                });
    }
}

package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@DisplayName("9. Asynchronous Effects Tests (Awaitility)")
public class AsynchronousEffectsApiTest extends BaseApiTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    @Test
    @DisplayName("Async effect appearance: transaction sent via Gateway is asynchronously logged and searchable")
    void testAsyncEffectAppearanceInLogger() {
        String pan = createCardViaApi(new BigDecimal("10000000"), new BigDecimal("10000000"));
        String stan = DataGenerator.generateStan();
        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withStan(stan)
                .withAmount(50000)
                .build();

        given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"));

        await().atMost(TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response searchResp = given()
                            .spec(gatewaySpec)
                            .queryParam("pan", pan)
                            .when()
                            .get("/api/transactions/search");

                    searchResp.then()
                            .statusCode(200)
                            .body("total", greaterThanOrEqualTo(1))
                            .body("transactions.findAll { it.stan == '" + stan + "' }.size()", greaterThanOrEqualTo(1));
                });
    }

    @Test
    @DisplayName("Async effect absence: card balance remains intact when transaction is rejected")
    void testCardBalanceRemainsIntactOnDeclinedTransaction() {
        BigDecimal initialBalance = new BigDecimal("5000");
        String pan = createCardViaApi(initialBalance, new BigDecimal("10000000"));

        Map<String, Object> request = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(50000)
                .build();

        given()
                .spec(gatewaySpec)
                .body(request)
                .when()
                .post("/api/transactions")
                .then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("51"));

        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofMillis(1500))
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    given()
                            .spec(gatewaySpec)
                            .when()
                            .get("/api/cards/" + pan)
                            .then()
                            .statusCode(200)
                            .body("availableBalance", equalTo(5000));
                });
    }
}

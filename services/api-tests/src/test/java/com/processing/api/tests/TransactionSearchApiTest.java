package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
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

@DisplayName("5. Transaction Search & Export Tests")
public class TransactionSearchApiTest extends BaseApiTest {

    @Test
    @DisplayName("GET /api/transactions/search finds transactions by PAN with GPath assertion")
    void testSearchByPan() {
        String pan = createCardViaApi(new BigDecimal("10000000"), new BigDecimal("10000000"));
        Map<String, Object> tx = TransactionRequestBuilder.aTransaction()
                .withPan(pan)
                .withAmount(150000)
                .build();
        given().spec(gatewaySpec).body(tx).when().post("/api/transactions");

        await().atMost(Duration.ofSeconds(5))
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
                            .body("transactions", notNullValue())
                            .body("transactions.findAll { it.pan == '" + pan + "' }.size()", greaterThanOrEqualTo(1));
                });
    }

    @Test
    @DisplayName("GET /api/transactions/search for non-existent PAN returns 200 with total 0 and empty array")
    void testSearchEmptyResultSetReturns200WithZeroTotalAndEmptyArray() {
        String unusedPan = "4000009999999991";

        Response response = given()
                .spec(gatewaySpec)
                .queryParam("pan", unusedPan)
                .when()
                .get("/api/transactions/search");

        response.then()
                .statusCode(200)
                .body("total", equalTo(0))
                .body("transactions", empty());
    }

    @Test
    @DisplayName("GET /api/transactions/export returns CSV file with Content-Disposition header")
    void testExportCsvTransactions() {
        Response response = given()
                .spec(gatewaySpec)
                .when()
                .get("/api/transactions/export");

        response.then()
                .statusCode(200)
                .header("Content-Type", startsWith("text/csv"))
                .header("Content-Disposition", startsWith("attachment; filename="));
    }
}

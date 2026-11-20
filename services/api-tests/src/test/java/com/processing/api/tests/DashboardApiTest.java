package com.processing.api.tests;

import com.processing.api.base.BaseApiTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("7. Dashboard Endpoint Tests")
public class DashboardApiTest extends BaseApiTest {

    @Test
    @DisplayName("GET /api/dashboard/stats returns aggregated metrics matching contract")
    void testDashboardStatsReturns200AndValidMetrics() {
        Response response = given()
                .spec(gatewaySpec)
                .when()
                .get("/api/dashboard/stats");

        response.then()
                .statusCode(200)
                .body("totalTransactions", greaterThanOrEqualTo(0))
                .body("approvedCount", greaterThanOrEqualTo(0))
                .body("declinedCount", greaterThanOrEqualTo(0))
                .body("approvalRate", both(greaterThanOrEqualTo(0.0f)).and(lessThanOrEqualTo(1.0f)))
                .body("totalAmount", greaterThanOrEqualTo(0))
                .body("averageAmount", greaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("GET /api/dashboard/recent returns transactions array")
    void testDashboardRecentReturnsTransactionsArray() {
        Response response = given()
                .spec(gatewaySpec)
                .queryParam("limit", 10)
                .when()
                .get("/api/dashboard/recent");

        response.then()
                .statusCode(200)
                .body("$", notNullValue());
    }
}

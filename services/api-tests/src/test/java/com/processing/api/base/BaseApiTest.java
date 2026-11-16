package com.processing.api.base;

import com.processing.api.support.CardRequestBuilder;
import com.processing.api.support.DataGenerator;
import com.processing.api.support.TestConfig;
import com.processing.api.support.mock.SmpMockServer;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;

public abstract class BaseApiTest {

    @BeforeEach
    public void resetLimits() {
        SmpMockServer.resetRateLimit();
    }

    protected static RequestSpecification gatewaySpec;
    protected static RequestSpecification cardMgmtSpec;
    protected static RequestSpecification switchSpec;
    protected static RequestSpecification authSpec;
    protected static RequestSpecification loggerSpec;
    protected static RequestSpecification terminalSpec;
    protected static RequestSpecification merchantSpec;
    protected static RequestSpecification binLookupSpec;
    protected static RequestSpecification notificationSpec;

    @BeforeAll
    public static void setUpEnvironment() {
        if (!TestConfig.isLiveStackRunning()) {
            SmpMockServer.startIfNeeded();
        }

        gatewaySpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getGatewayUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        cardMgmtSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getCardMgmtUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        switchSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getSwitchUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        authSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getAuthUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        loggerSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getLoggerUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        terminalSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getTerminalUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        merchantSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getMerchantUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        binLookupSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getBinLookupUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        notificationSpec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.getNotificationUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();
    }

    
    protected String createCardViaApi(BigDecimal balance, BigDecimal dailyLimit) {
        String pan = DataGenerator.generatePan("400000");
        Map<String, Object> body = CardRequestBuilder.aCard()
                .withBin("400000")
                .withInitialBalance(balance)
                .withDailyLimit(dailyLimit)
                .build();

        Response response = given()
                .spec(gatewaySpec)
                .body(body)
                .when()
                .post("/api/cards");

        return response.then().extract().path("pan");
    }

    
    protected String createDefaultActiveCard() {
        return createCardViaApi(new BigDecimal("1000000"), new BigDecimal("15000000"));
    }
}

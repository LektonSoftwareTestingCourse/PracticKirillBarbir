package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.config.SwitchProperties;
import com.processing.exception.AuthorizationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthorizationClientTest {

    private static final String AUTH_URL = "http://auth-service:8080";
    private static final String AUTHORIZE_URI = AUTH_URL + "/api/internal/authorize";
    private static final String ROLLBACK_URI = AUTH_URL + "/api/internal/rollback";
    private static final String HEALTH_URI = AUTH_URL + "/health";
    private static final String STAN = "000001";
    private static final String RRN = "123456789012";

    private MockRestServiceServer mockServer;
    private CircuitBreaker circuitBreaker;
    private AuthorizationClient client;

    @BeforeEach
    void setUp() {
        SwitchProperties properties = mock(SwitchProperties.class);
        when(properties.authorizationUrl()).thenReturn(AUTH_URL);
        SwitchProperties.RetryProperties retryConfig = mock(SwitchProperties.RetryProperties.class);
        when(retryConfig.maxAttempts()).thenReturn(3);
        when(properties.retry()).thenReturn(retryConfig);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        Retry retry = Retry.of("testRetry", RetryConfig.custom().maxAttempts(1).build());
        circuitBreaker = CircuitBreaker.ofDefaults("testCb");

        client = new AuthorizationClient(properties, restClient, retry, circuitBreaker);
    }

    @Test
    void shouldReturnApprovedWhenAuthorizationCallSucceeds() {
        // Arrange
        String json = "{\"mti\":\"0110\",\"stan\":\"000001\",\"rrn\":\"123456789012\",\"status\":\"APPROVED\"}";
        mockServer.expect(requestTo(AUTHORIZE_URI))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        // Act
        AuthorizationResponse response = client.authorize(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.rrn()).isEqualTo(RRN);
        mockServer.verify();
    }

    @Test
    void shouldReturnDeclinedWhenAuthorizationCallDeclines() {
        // Arrange
        String json = "{\"mti\":\"0110\",\"stan\":\"000001\",\"status\":\"DECLINED\",\"declineReason\":\"CARD_BLOCKED\"}";
        mockServer.expect(requestTo(AUTHORIZE_URI))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        // Act
        AuthorizationResponse response = client.authorize(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo("CARD_BLOCKED");
        mockServer.verify();
    }

    @Test
    void shouldThrowAuthorizationExceptionWhenStatusIsInvalid() {
        // Arrange
        String json = "{\"mti\":\"0110\",\"stan\":\"000001\",\"status\":\"UNKNOWN_STATUS\"}";
        mockServer.expect(requestTo(AUTHORIZE_URI))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThatThrownBy(() -> client.authorize(validRequest()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Invalid response from Authorization");
    }

    @Test
    void shouldThrowAuthorizationExceptionWhenCircuitBreakerIsOpen() {
        // Arrange
        circuitBreaker.transitionToOpenState();

        // Act & Assert
        assertThatThrownBy(() -> client.authorize(validRequest()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Circuit breaker open");
    }

    @Test
    void shouldReturnApprovedRollbackWhenCallSucceeds() {
        // Arrange
        String json = "{\"rrn\":\"123456789012\",\"responseCode\":\"00\",\"status\":\"APPROVED\"}";
        mockServer.expect(requestTo(ROLLBACK_URI))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        // Act
        RollbackResponse response = client.rollback(validRequest(), RRN);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("APPROVED");
        mockServer.verify();
    }

    @Test
    void shouldReturnDeclinedRollbackWhenCallDeclined() {
        // Arrange
        String json = "{\"rrn\":\"123456789012\",\"responseCode\":\"05\",\"status\":\"DECLINED\"}";
        mockServer.expect(requestTo(ROLLBACK_URI))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        // Act
        RollbackResponse response = client.rollback(validRequest(), RRN);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DECLINED");
        mockServer.verify();
    }

    @Test
    void shouldReturnNullWhenRollbackReturnsServerError() {
        // Arrange
        mockServer.expect(requestTo(ROLLBACK_URI))
                .andRespond(withServerError());

        // Act
        RollbackResponse response = client.rollback(validRequest(), RRN);

        // Assert
        assertThat(response).isNull();
        mockServer.verify();
    }

    @Test
    void shouldReturnOkWhenHealthCheckSucceeds() {
        // Arrange
        mockServer.expect(requestTo(HEALTH_URI))
                .andRespond(withSuccess());

        // Act
        String health = client.checkHealth();

        // Assert
        assertThat(health).isEqualTo("ok");
        mockServer.verify();
    }

    @Test
    void shouldReturnDownWhenHealthCheckFails() {
        // Arrange
        mockServer.expect(requestTo(HEALTH_URI))
                .andRespond(withServerError());

        // Act
        String health = client.checkHealth();

        // Assert
        assertThat(health).isEqualTo("down");
        mockServer.verify();
    }

    private AuthorizationRequest validRequest() {
        return new AuthorizationRequest(
                "0100", STAN, "4000001234567890", "000000",
                new BigDecimal("1000"), "643", Instant.now(),
                "TERM0001", "POS", "MERCH0000000002", "5411", "ACQ001", "BANK01"
        );
    }
}

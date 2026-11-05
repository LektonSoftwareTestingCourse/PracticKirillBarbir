package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.exception.AuthorizationException;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final String PAN = "4000001234567890";
    private static final String ISSUER_ID = "BANK_1";
    private static final String STAN = "000001";
    private static final String RRN = "123456789012";
    private static final BigDecimal AMOUNT = new BigDecimal("1000");

    @Mock
    private RoutingService routingService;

    @Mock
    private AuthorizationClient authorizationClient;

    @Mock
    private AcquiringFeeClient acquiringFeeClient;

    @Mock
    private LoggerClient loggerClient;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(
                routingService,
                authorizationClient,
                acquiringFeeClient,
                loggerClient
        );
    }

    @Test
    void shouldApproveAndLogWhenAuthorizationSucceeds() {
        // Arrange
        AuthorizationRequest request = createRequest(PAN);
        AuthorizationResponse authResponse = createApprovedResponse();

        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(authResponse);
        when(acquiringFeeClient.fetchAcquiringFee(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("15.50"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.responseCode()).isEqualTo("00");
        verify(loggerClient).log(any());
        verify(authorizationClient, never()).rollback(any(), any());
    }

    @Test
    void shouldDeclineWhenUnknownBinExceptionThrown() {
        // Arrange
        AuthorizationRequest request = createRequest(PAN);
        when(routingService.getIssuerIdByPan(PAN)).thenThrow(new UnknownBinException("400000"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo("CARD_NOT_FOUND");
        verify(authorizationClient, never()).authorize(any());
    }

    @Test
    void shouldDeclineWhenPanIsShortAndUnknownBin() {
        // Arrange
        AuthorizationRequest request = createRequest("123");
        when(routingService.getIssuerIdByPan("123")).thenThrow(new UnknownBinException("null-or-short"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo("CARD_NOT_FOUND");
    }

    @Test
    void shouldDeclineWhenAuthorizationExceptionThrown() {
        // Arrange
        AuthorizationRequest request = createRequest(PAN);
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any()))
                .thenThrow(new AuthorizationException(STAN, 3, "Connection timeout"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo("Authorization service unavailable");
    }

    @Test
    void shouldRollbackAndReturnSystemErrorWhenQueuePublishFailsForApprovedTx() {
        // Arrange
        AuthorizationRequest request = createRequest(PAN);
        AuthorizationResponse authResponse = createApprovedResponse();
        RollbackResponse rollbackResponse = rollbackResponse("00", "APPROVED", null);

        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(authResponse);
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), eq(RRN))).thenReturn(rollbackResponse);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("96");
        assertThat(response.declineReason()).isEqualTo("System error");
        verify(authorizationClient).rollback(any(), eq(RRN));
    }

    @Test
    void shouldHandleDeclinedRollbackWhenQueuePublishFails() {
        // Arrange
        AuthorizationRequest request = createRequest(PAN);
        AuthorizationResponse authResponse = createApprovedResponse();
        RollbackResponse rollbackResponse = rollbackResponse("05", "DECLINED", "CARD_BLOCKED");

        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(authResponse);
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), eq(RRN))).thenReturn(rollbackResponse);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("96");
    }

    @Test
    void shouldHandleNullRollbackResponseWhenQueuePublishFails() {
        // Arrange
        AuthorizationRequest request = createRequest(PAN);
        AuthorizationResponse authResponse = createApprovedResponse();

        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(authResponse);
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(), eq(RRN))).thenReturn(null);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("96");
    }

    @Test
    void shouldNotRollbackWhenQueuePublishFailsForDeclinedTx() {
        // Arrange
        AuthorizationRequest request = createRequest(PAN);
        AuthorizationResponse authResponse = createDeclinedResponse("05", "CARD_BLOCKED");

        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(authResponse);
        when(loggerClient.log(any())).thenReturn(false);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("05");
        verify(authorizationClient, never()).rollback(any(), any());
    }

    @Test
    void shouldHandleNullProcessingTimeInApprovedResponse() {
        // Arrange
        AuthorizationRequest request = createRequest(PAN);
        AuthorizationResponse authResponse = createApprovedResponse(null);

        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(authResponse);
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        verify(loggerClient).log(any());
    }

    private AuthorizationRequest createRequest(String pan) {
        return new AuthorizationRequest(
                "0100", STAN, pan, "000000",
                AMOUNT, "643", Instant.now(),
                "TERM0001", "POS", "MERCH0000000002", "5411", "ACQ001", null
        );
    }

    private AuthorizationResponse createApprovedResponse() {
        return createApprovedResponse(42);
    }

    private AuthorizationResponse createApprovedResponse(Integer processingTimeMs) {
        return new AuthorizationResponse(
                "0110", STAN, RRN, "AUTH01", "00", "APPROVED", null, processingTimeMs
        );
    }

    private AuthorizationResponse createDeclinedResponse(String code, String declineReason) {
        return new AuthorizationResponse(
                "0110", STAN, null, null, code, "DECLINED", declineReason, 25
        );
    }

    private RollbackResponse rollbackResponse(String code, String status, String declineReason) {
        return new RollbackResponse(RRN, code, status, declineReason, 15);
    }
}

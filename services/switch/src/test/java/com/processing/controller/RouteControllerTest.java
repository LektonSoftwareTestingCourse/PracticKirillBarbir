package com.processing.controller;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.service.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RouteControllerTest {

    private RouteService routeService;
    private RouteController routeController;

    @BeforeEach
    void setUp() {
        routeService = mock(RouteService.class);
        routeController = new RouteController(routeService);
    }

    @Test
    void shouldRouteTransactionSuccessfully() {
        // Arrange
        AuthorizationRequest request = requestWithPan("4000001234567890");
        AuthorizationResponse expectedResponse = approvedResponse();
        when(routeService.route(any())).thenReturn(expectedResponse);

        // Act
        ResponseEntity<AuthorizationResponse> responseEntity = routeController.routeTransaction(request);

        // Assert
        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(responseEntity.getBody()).isEqualTo(expectedResponse);
        verify(routeService).route(request);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "123", "1234567"})
    void shouldHandleShortPanInLoggingWithoutError(String shortPan) {
        // Arrange
        AuthorizationRequest request = requestWithPan(shortPan);
        AuthorizationResponse expectedResponse = AuthorizationResponse.unknownBin("000001");
        when(routeService.route(any())).thenReturn(expectedResponse);

        // Act
        ResponseEntity<AuthorizationResponse> responseEntity = routeController.routeTransaction(request);

        // Assert
        assertThat(responseEntity.getBody()).isEqualTo(expectedResponse);
    }

    private AuthorizationRequest requestWithPan(String pan) {
        return new AuthorizationRequest(
                "0100", "000001", pan, "000000",
                new BigDecimal("100"), "643", Instant.now(),
                "TERM01", "POS", "MERCH01", "5411", "ACQ01", null
        );
    }

    private AuthorizationResponse approvedResponse() {
        return new AuthorizationResponse(
                "0110", "000001", "123456789012", "AUTH01", "00", "APPROVED", null, 20
        );
    }
}

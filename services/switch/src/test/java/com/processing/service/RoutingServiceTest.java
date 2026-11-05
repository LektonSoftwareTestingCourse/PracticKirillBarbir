package com.processing.service;

import com.processing.config.SwitchProperties;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingServiceTest {

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        SwitchProperties properties = mock(SwitchProperties.class);
        when(properties.binRouting()).thenReturn(Map.of("400000", "BANK_A", "500000", "BANK_B"));
        routingService = new RoutingService(properties);
    }

    @Test
    void shouldReturnIssuerIdWhenPanValidAndKnownBin() {
        // Act
        String issuerId = routingService.getIssuerIdByPan("4000001234567890");

        // Assert
        assertThat(issuerId).isEqualTo("BANK_A");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "12", "123", "1234", "12345"})
    void shouldThrowUnknownBinExceptionWhenPanNullOrShorterThanSix(String pan) {
        // Act & Assert
        assertThatThrownBy(() -> routingService.getIssuerIdByPan(pan))
                .isInstanceOf(UnknownBinException.class);
    }

    @Test
    void shouldThrowUnknownBinExceptionWhenBinIsUnknown() {
        // Act & Assert
        assertThatThrownBy(() -> routingService.getIssuerIdByPan("9999991234567890"))
                .isInstanceOf(UnknownBinException.class);
    }
}

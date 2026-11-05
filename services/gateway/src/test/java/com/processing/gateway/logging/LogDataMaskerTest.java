package com.processing.gateway.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LogDataMaskerTest {

    private LogDataMasker masker;

    @BeforeEach
    void setUp() {
        masker = new LogDataMasker();
    }

    @ParameterizedTest
    @CsvSource({
            "'{\"pan\":\"4000001234567890\"}', '{\"pan\":\"400000****7890\"}'",
            "'{\"pan\": \"2200001234567899\"}', '{\"pan\":\"220000****7899\"}'",
            "'{\"PAN\":\"5555551234567890\"}', '{\"pan\":\"555555****7890\"}'"
    })
    void shouldMaskPanInJsonPayload(String input, String expected) {
        // Act
        String result = masker.maskData(input);

        // Assert
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "'{\"cvv\":\"123\"}', '{\"cvv\":\"***\"}'",
            "'{\"cvv\": \"1234\"}', '{\"cvv\":\"***\"}'",
            "'{\"CVV\":\"999\"}', '{\"cvv\":\"***\"}'"
    })
    void shouldMaskCvvInJsonPayload(String input, String expected) {
        // Act
        String result = masker.maskData(input);

        // Assert
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldMaskBothPanAndCvvWhenPresentTogether() {
        // Arrange
        String input = "{\"pan\":\"4000001234567890\",\"cvv\":\"123\",\"amount\":100.50}";

        // Act
        String result = masker.maskData(input);

        // Assert
        assertThat(result).contains("\"pan\":\"400000****7890\"");
        assertThat(result).contains("\"cvv\":\"***\"");
        assertThat(result).contains("\"amount\":100.50");
    }

    @Test
    void shouldNotAlterPayloadWithoutSensitiveData() {
        // Arrange
        String input = "{\"orderId\":\"ORD-123\",\"status\":\"COMPLETED\"}";

        // Act
        String result = masker.maskData(input);

        // Assert
        assertThat(result).isEqualTo(input);
    }
}

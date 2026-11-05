package com.processing.cardmanagement.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnValidatorTest {

    private LuhnValidator luhnValidator;

    @BeforeEach
    void setUp() {
        luhnValidator = new LuhnValidator();
    }

    @Test
    void shouldGenerateValidPanStartingWithGivenBin() {
        // Arrange
        String bin = "400000";

        // Act
        String pan = luhnValidator.generatePan(bin);

        // Assert
        assertThat(pan).isNotNull();
        assertThat(pan).hasSize(16);
        assertThat(pan).startsWith(bin);
        assertThat(luhnValidator.isValid(pan)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0000000000000000",
            "0000000000000018",
            "0000000000000026"
    })
    void shouldReturnTrueWhenPanSatisfiesLuhnAlgorithm(String validPan) {
        // Act
        boolean result = luhnValidator.isValid(validPan);

        // Assert
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0000000000000001",
            "0000000000000019",
            "0000000000000027"
    })
    void shouldReturnFalseWhenPanViolatesLuhnAlgorithm(String invalidPan) {
        // Act
        boolean result = luhnValidator.isValid(invalidPan);

        // Assert
        assertThat(result).isFalse();
    }
}

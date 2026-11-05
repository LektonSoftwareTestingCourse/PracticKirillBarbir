package com.processing.gateway.validation;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRequestValidatorTest {

    private TransactionRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TransactionRequestValidator();
    }

    @Test
    void shouldPassValidationWhenRequestIsValid() {
        // Arrange
        AuthorizationRequest request = validRequest();

        // Act & Assert
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void shouldThrowWhenRequestIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("Request body is required");
    }

    @Test
    void shouldThrowWhenMtiIsNot0100() {
        // Arrange
        AuthorizationRequest request = requestWithMti("0200");

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("mti");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "400000123456789", "40000012345678901", "400000123456789A"})
    void shouldThrowWhenPanIsNotExactlySixteenDigits(String invalidPan) {
        // Arrange
        AuthorizationRequest request = requestWithPan(invalidPan);

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("pan");
    }

    @Test
    void shouldThrowWhenAmountIsNull() {
        // Arrange
        AuthorizationRequest request = requestWithAmount(null);

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("amount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-100.50"})
    void shouldThrowWhenAmountIsZeroOrNegative(String invalidAmount) {
        // Arrange
        AuthorizationRequest request = requestWithAmount(new BigDecimal(invalidAmount));

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldThrowWhenCurrencyCodeIsNotThreeCharacters() {
        // Arrange
        AuthorizationRequest request = requestWithCurrency("64");

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("currencyCode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12345", "ABCD"})
    void shouldThrowWhenMccIsNotFourDigits(String invalidMcc) {
        // Arrange
        AuthorizationRequest request = requestWithMcc(invalidMcc);

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("mcc");
    }

    @Test
    void shouldThrowWhenStanIsBlank() {
        // Arrange
        AuthorizationRequest request = requestWithStan("   ");

        // Act & Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("stan");
    }

    private AuthorizationRequest validRequest() {
        return createRequest("0100", "000001", "4000001234567890", new BigDecimal("100.50"), "643", "5411");
    }

    private AuthorizationRequest requestWithMti(String mti) {
        return createRequest(mti, "000001", "4000001234567890", new BigDecimal("100"), "643", "5411");
    }

    private AuthorizationRequest requestWithPan(String pan) {
        return createRequest("0100", "000001", pan, new BigDecimal("100"), "643", "5411");
    }

    private AuthorizationRequest requestWithAmount(BigDecimal amount) {
        return createRequest("0100", "000001", "4000001234567890", amount, "643", "5411");
    }

    private AuthorizationRequest requestWithCurrency(String currencyCode) {
        return createRequest("0100", "000001", "4000001234567890", new BigDecimal("100"), currencyCode, "5411");
    }

    private AuthorizationRequest requestWithMcc(String mcc) {
        return createRequest("0100", "000001", "4000001234567890", new BigDecimal("100"), "643", mcc);
    }

    private AuthorizationRequest requestWithStan(String stan) {
        return createRequest("0100", stan, "4000001234567890", new BigDecimal("100"), "643", "5411");
    }

    private AuthorizationRequest createRequest(
            String mti, String stan, String pan, BigDecimal amount, String currencyCode, String mcc
    ) {
        return new AuthorizationRequest(
                mti, stan, pan, "000000",
                amount, currencyCode, Instant.now(),
                "TERM0001", "POS", "MERCH0000000002", mcc, "ACQ001", null
        );
    }
}

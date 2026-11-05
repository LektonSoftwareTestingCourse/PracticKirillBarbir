package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRequestNormalizerTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldReturnTerminalIdAsIsWhenNullOrBlank(String terminalId) {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeTerminalId(terminalId);

        // Assert
        assertThat(result).isEqualTo(terminalId);
    }

    @Test
    void shouldTruncateTerminalIdWhenLongerThanEight() {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeTerminalId("TERM12345678");

        // Assert
        assertThat(result).isEqualTo("TERM1234");
    }

    @Test
    void shouldPadTerminalIdWhenShorterThanEight() {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeTerminalId("TERM");

        // Assert
        assertThat(result).isEqualTo("TERM0000");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldReturnMerchantIdAsIsWhenNullOrBlank(String merchantId) {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeMerchantId(merchantId);

        // Assert
        assertThat(result).isEqualTo(merchantId);
    }

    @Test
    void shouldReturnMerchantIdWhenExactlyFifteen() {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeMerchantId("MERCH1234567890");

        // Assert
        assertThat(result).isEqualTo("MERCH1234567890");
    }

    @Test
    void shouldStripLeadingZeroAfterPrefixWhenSixteenAndMerch0() {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeMerchantId("MERCH01234567890");

        // Assert
        assertThat(result).isEqualTo("MERCH1234567890");
    }

    @Test
    void shouldTruncateMerchantIdWhenLongerThanFifteenAndNotMerch0() {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeMerchantId("OTHER1234567890EXTRA");

        // Assert
        assertThat(result).isEqualTo("OTHER1234567890");
    }

    @Test
    void shouldPadMerchantIdWhenShorterThanFifteen() {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeMerchantId("MERCH123");

        // Assert
        assertThat(result).isEqualTo("MERCH1230000000");
    }

    @Test
    void shouldTruncateMerchantIdWhenSixteenAndNotStartingWithMerch() {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeMerchantId("OTHER01234567890");

        // Assert
        assertThat(result).isEqualTo("OTHER0123456789");
    }

    @Test
    void shouldTruncateMerchantIdWhenSixteenAndMerchWithoutLeadingZero() {
        // Act
        String result = AuthorizationRequestNormalizer.normalizeMerchantId("MERCH91234567890");

        // Assert
        assertThat(result).isEqualTo("MERCH9123456789");
    }

    @Test
    void shouldNormalizeRequestFields() {
        // Arrange
        AuthorizationRequest original = new AuthorizationRequest(
                "0100", "123456", "4000001234567890", "000000",
                new BigDecimal("500"), "643", Instant.now(),
                "T1", "POS", "M1", "5411", "ACQ01", null
        );

        // Act
        AuthorizationRequest normalized = AuthorizationRequestNormalizer.normalize(original);

        // Assert
        assertThat(normalized.terminalId()).isEqualTo("T1000000");
        assertThat(normalized.merchantId()).isEqualTo("M10000000000000");
        assertThat(normalized.stan()).isEqualTo(original.stan());
    }
}

package com.processing.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationExceptionTest {

    @Test
    void shouldFormatMessageWithLastError() {
        // Arrange & Act
        AuthorizationException exception = new AuthorizationException("000001", 3, "Network timeout");

        // Assert
        assertThat(exception.getStan()).isEqualTo("000001");
        assertThat(exception.getMessage()).contains("STAN=000001");
        assertThat(exception.getMessage()).contains("3 attempts");
        assertThat(exception.getMessage()).contains("Network timeout");
    }

    @Test
    void shouldFormatMessageWithUnknownWhenLastErrorIsNull() {
        // Arrange & Act
        AuthorizationException exception = new AuthorizationException("000002", 2, null);

        // Assert
        assertThat(exception.getStan()).isEqualTo("000002");
        assertThat(exception.getMessage()).contains("Last error: unknown");
    }
}

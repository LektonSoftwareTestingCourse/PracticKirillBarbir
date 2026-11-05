package com.processing.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
    }

    @ParameterizedTest
    @CsvSource({
            "'192.168.1.100', '192.168.1.100'",
            "'192.168.1.100, 10.0.0.1, 172.16.0.1', '192.168.1.100'",
            "'  192.168.1.100  ', '192.168.1.100'"
    })
    void shouldResolveClientIpFromXForwardedForHeader(String headerValue, String expectedIp) {
        // Arrange
        MockServerWebExchange exchange = exchangeWithHeader("X-Forwarded-For", headerValue);

        // Act
        String resolvedIp = resolver.resolve(exchange);

        // Assert
        assertThat(resolvedIp).isEqualTo(expectedIp);
    }

    @Test
    void shouldResolveClientIpFromXRealIpWhenForwardedForIsMissing() {
        // Arrange
        MockServerWebExchange exchange = exchangeWithHeader("X-Real-IP", "  10.20.30.40  ");

        // Act
        String resolvedIp = resolver.resolve(exchange);

        // Assert
        assertThat(resolvedIp).isEqualTo("10.20.30.40");
    }

    @Test
    void shouldPreferXForwardedForOverXRealIp() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/transactions")
                .header("X-Forwarded-For", "192.168.1.10")
                .header("X-Real-IP", "10.20.30.40")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        String resolvedIp = resolver.resolve(exchange);

        // Assert
        assertThat(resolvedIp).isEqualTo("192.168.1.10");
    }

    @Test
    void shouldFallbackToRemoteAddressWhenHeadersAreMissing() {
        // Arrange
        MockServerWebExchange exchange = exchangeWithRemoteAddress(new InetSocketAddress("127.0.0.1", 8080));

        // Act
        String resolvedIp = resolver.resolve(exchange);

        // Assert
        assertThat(resolvedIp).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldReturnUnknownWhenNoIpSourceIsAvailable() {
        // Arrange
        MockServerWebExchange exchange = exchangeWithRemoteAddress(InetSocketAddress.createUnresolved("unresolved-host", 8080));

        // Act
        String resolvedIp = resolver.resolve(exchange);

        // Assert
        assertThat(resolvedIp).isEqualTo("unknown");
    }

    private MockServerWebExchange exchangeWithHeader(String headerName, String headerValue) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/transactions")
                .header(headerName, headerValue)
                .build();
        return MockServerWebExchange.from(request);
    }

    private MockServerWebExchange exchangeWithRemoteAddress(InetSocketAddress address) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/transactions")
                .remoteAddress(address)
                .build();
        return MockServerWebExchange.from(request);
    }
}

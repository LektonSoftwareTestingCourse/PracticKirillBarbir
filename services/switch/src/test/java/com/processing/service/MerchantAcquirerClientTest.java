package com.processing.service;

import com.processing.config.SwitchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MerchantAcquirerClientTest {

    private static final String BASE_URL = "http://merchant-acquirer:8080";
    private static final String FEE_URL = BASE_URL + "/api/simulator/merchant/fee";

    private MockRestServiceServer mockServer;
    private MerchantAcquirerClient client;

    @BeforeEach
    void setUp() {
        SwitchProperties properties = mock(SwitchProperties.class);
        when(properties.merchantAcquirerUrl()).thenReturn(BASE_URL);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        client = new MerchantAcquirerClient(properties, restClient);
    }

    @Test
    void shouldReturnFeeWhenResponseIsSuccessful() {
        // Arrange
        mockServer.expect(requestTo(FEE_URL))
                .andRespond(withSuccess("{\"acquirerFee\": 15.50}", MediaType.APPLICATION_JSON));

        // Act
        BigDecimal fee = fetchDefaultFee();

        // Assert
        assertThat(fee).isEqualByComparingTo(new BigDecimal("15.50"));
        mockServer.verify();
    }

    @Test
    void shouldReturnNullWhenResponseHasNullFee() {
        // Arrange
        mockServer.expect(requestTo(FEE_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // Act
        BigDecimal fee = fetchDefaultFee();

        // Assert
        assertThat(fee).isNull();
        mockServer.verify();
    }

    @Test
    void shouldReturnNullWhenServerErrorOccurs() {
        // Arrange
        mockServer.expect(requestTo(FEE_URL))
                .andRespond(withServerError());

        // Act
        BigDecimal fee = fetchDefaultFee();

        // Assert
        assertThat(fee).isNull();
        mockServer.verify();
    }

    private BigDecimal fetchDefaultFee() {
        return client.fetchAcquiringFee(
                Instant.now(), "000001", "4000001234567890", "TERM01", new BigDecimal("1000")
        );
    }
}

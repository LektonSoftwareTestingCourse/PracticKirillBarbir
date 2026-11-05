package com.processing.service;

import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import com.processing.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LoggerClientTest {

    private RabbitTemplate rabbitTemplate;
    private LoggerClient loggerClient;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        loggerClient = new LoggerClient(rabbitTemplate);
    }

    @Test
    void shouldReturnTrueWhenMessagePublishedSuccessfully() {
        // Arrange
        TransactionRequest transaction = createTransaction();

        // Act
        boolean result = loggerClient.log(transaction);

        // Assert
        assertThat(result).isTrue();
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY),
                eq(transaction)
        );
    }

    @Test
    void shouldReturnFalseWhenAmqpExceptionThrown() {
        // Arrange
        TransactionRequest transaction = createTransaction();
        doThrow(new AmqpException("Broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY), eq(transaction));

        // Act
        boolean result = loggerClient.log(transaction);

        // Assert
        assertThat(result).isFalse();
    }

    private TransactionRequest createTransaction() {
        return new TransactionRequest(
                UUID.randomUUID(), "0100", "000001", "123456789012",
                "4000001234567890", "000000", new BigDecimal("100"), "643",
                "TERM01", "POS", "MERCH01", "5411", "ACQ01", "BANK01",
                null, TransactionStatus.APPROVED, null, "AUTH01", 10,
                Instant.now(), Instant.now()
        );
    }
}

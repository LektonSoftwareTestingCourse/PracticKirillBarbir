package com.processing.cardmanagement.services;

import com.processing.cardmanagement.config.RabbitMQConfig;
import com.processing.cardmanagement.events.CardServiceCreationEvent;
import com.processing.cardmanagement.models.CardOutboxEventData;
import com.processing.cardmanagement.models.OutboxEventDataStatus;
import com.processing.cardmanagement.options.OutboxOptions;
import com.processing.cardmanagement.repositories.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorImplTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxOptions outboxOptions;
    private OutboxEventProcessorImpl eventProcessor;

    @BeforeEach
    void setUp() {
        outboxOptions = new OutboxOptions(1000, 3);
        eventProcessor = new OutboxEventProcessorImpl(outboxRepository, rabbitTemplate, outboxOptions);
    }

    @Test
    void shouldPublishEventAndSaveProcessedStatusWhenRabbitSucceeds() {
        // Arrange
        CardOutboxEventData eventData = createEventData();

        // Act
        eventProcessor.processSingleEvent(eventData);

        // Assert
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.CARD_EVENTS_EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_PREFIX + eventData.event().getClass().getSimpleName()),
                eq(eventData.event())
        );

        ArgumentCaptor<CardOutboxEventData> captor = ArgumentCaptor.forClass(CardOutboxEventData.class);
        verify(outboxRepository).save(captor.capture());
        CardOutboxEventData saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(OutboxEventDataStatus.PROCESSED);
        assertThat(saved.processedAt()).isNotNull();
    }

    @Test
    void shouldIncrementRetryWhenRabbitFailsAndRetriesRemain() {
        // Arrange
        CardOutboxEventData eventData = createEventData();

        doThrow(new RuntimeException("Rabbit down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        // Act
        eventProcessor.processSingleEvent(eventData);

        // Assert
        ArgumentCaptor<CardOutboxEventData> captor = ArgumentCaptor.forClass(CardOutboxEventData.class);
        verify(outboxRepository).save(captor.capture());
        CardOutboxEventData saved = captor.getValue();
        assertThat(saved.retryCount()).isEqualTo(1);
        assertThat(saved.status()).isEqualTo(OutboxEventDataStatus.PENDING);
        assertThat(saved.lastError()).contains("Rabbit down");
    }

    @Test
    void shouldMarkFailedWhenMaxRetriesExceeded() {
        // Arrange
        // Current retry count is 2; next fail reaches maxRetryCount = 3
        CardOutboxEventData eventData = createEventData()
                .withRetry("err1")
                .withRetry("err2");

        doThrow(new RuntimeException("Rabbit persistent failure"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        // Act
        eventProcessor.processSingleEvent(eventData);

        // Assert
        ArgumentCaptor<CardOutboxEventData> captor = ArgumentCaptor.forClass(CardOutboxEventData.class);
        verify(outboxRepository).save(captor.capture());
        CardOutboxEventData saved = captor.getValue();
        assertThat(saved.retryCount()).isEqualTo(3);
        assertThat(saved.status()).isEqualTo(OutboxEventDataStatus.FAILED);
    }

    private CardOutboxEventData createEventData() {
        return new CardOutboxEventData(new CardServiceCreationEvent(100L));
    }
}

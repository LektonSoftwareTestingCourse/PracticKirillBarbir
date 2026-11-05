package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.constants.DeclineOutcome;
import com.processing.authorization.events.AuthorizationEvent;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.exceptions.RollbackConflictException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackRequest;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PAN = "4000001234567890";
    private static final String BIN = "400000";
    private static final String ISSUER_ID = "ISS001";
    private static final BigDecimal AMOUNT = new BigDecimal("1000");
    private static final Instant REQUEST_TIME = Instant.now();

    @Mock
    private LimitUsageRepository limitUsageRepository;

    @Mock
    private AuthorizationEventNotifier eventNotifier;

    @Mock
    private BinLookupClient binLookupClient;

    @Mock
    private CardManagementClient cardManagementClient;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                limitUsageRepository,
                eventNotifier,
                binLookupClient,
                cardManagementClient
        );
    }

    @Test
    void shouldApproveAuthorizationWhenCardIsActiveAndSufficientFunds() {
        // Arrange
        CardModel card = activeCard();
        when(cardManagementClient.getCard(PAN)).thenReturn(card);
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER_ID));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.rrn()).isNotBlank();
        assertThat(response.authCode()).isNotBlank();
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = CardModelStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"ACTIVE"})
    void shouldDeclineWhenCardStatusIsNotActive(CardModelStatus status) {
        // Arrange
        CardModel card = cardWithStatus(status);
        when(cardManagementClient.getCard(PAN)).thenReturn(card);
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER_ID));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenCardNotFound() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenThrow(new CardNotFoundException(PAN));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_CARD_NOT_FOUND);
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenCardIsExpired() {
        // Arrange
        CardModel card = cardWithExpiry(YearMonth.now(ZoneOffset.UTC).minusMonths(1));
        when(cardManagementClient.getCard(PAN)).thenReturn(card);
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER_ID));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_CARD_EXPIRED);
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenInsufficientFunds() {
        // Arrange
        CardModel card = cardWithBalance(new BigDecimal("500"));
        when(cardManagementClient.getCard(PAN)).thenReturn(card);
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER_ID));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_INSUFFICIENT_FUNDS);
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenLimitsNotUpdated() {
        // Arrange
        CardModel card = activeCard();
        when(cardManagementClient.getCard(PAN)).thenReturn(card);
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER_ID));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(0);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_EXCEEDS_AMOUNT_LIMIT);
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineWhenCardStatusIsNull() {
        // Arrange
        CardModel card = cardWithStatus(null);
        when(cardManagementClient.getCard(PAN)).thenReturn(card);
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER_ID));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_UNKNOWN_REASON);
    }

    @Test
    void shouldApproveRollbackWhenRequestValid() {
        // Arrange
        RollbackRequest rollbackRequest = validRollbackRequest();

        // Act
        RollbackResponse response = authService.rollback(rollbackRequest, REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        verify(cardManagementClient).rollback(rollbackRequest);
        verify(eventNotifier).notify(any(AuthorizationEvent.class));
    }

    @Test
    void shouldDeclineRollbackWhenCardNotFound() {
        // Arrange
        RollbackRequest rollbackRequest = validRollbackRequest();
        doThrow(new CardNotFoundException(PAN)).when(cardManagementClient).rollback(rollbackRequest);

        // Act
        RollbackResponse response = authService.rollback(rollbackRequest, REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_TRANSACTION_NOT_FOUND);
    }

    @Test
    void shouldDeclineRollbackWhenConflict() {
        // Arrange
        RollbackRequest rollbackRequest = validRollbackRequest();
        doThrow(new RollbackConflictException("Conflict")).when(cardManagementClient).rollback(rollbackRequest);

        // Act
        RollbackResponse response = authService.rollback(rollbackRequest, REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_ALREADY_ROLLED_BACK);
    }

    private AuthorizationRequest validRequest() {
        return new AuthorizationRequest(
                "0100", "000001", PAN, "000000",
                AMOUNT, "643", REQUEST_TIME,
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", ISSUER_ID
        );
    }

    private CardModel activeCard() {
        return cardWithStatus(CardModelStatus.ACTIVE);
    }

    private CardModel cardWithStatus(CardModelStatus status) {
        return createCard(status, YearMonth.now(ZoneOffset.UTC).plusYears(2), new BigDecimal("10000"));
    }

    private CardModel cardWithExpiry(YearMonth expiry) {
        return createCard(CardModelStatus.ACTIVE, expiry, new BigDecimal("10000"));
    }

    private CardModel cardWithBalance(BigDecimal balance) {
        return createCard(CardModelStatus.ACTIVE, YearMonth.now(ZoneOffset.UTC).plusYears(2), balance);
    }

    private CardModel createCard(CardModelStatus status, YearMonth expiry, BigDecimal balance) {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                expiry, status, "643",
                new BigDecimal("100000"), new BigDecimal("500000"), balance,
                ISSUER_ID, Instant.now()
        );
    }

    private RollbackRequest validRollbackRequest() {
        return new RollbackRequest("123456789012", PAN, AMOUNT);
    }
}

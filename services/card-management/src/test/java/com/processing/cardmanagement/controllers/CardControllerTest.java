package com.processing.cardmanagement.controllers;

import com.processing.cardmanagement.mappers.CardRestMapper;
import com.processing.cardmanagement.mappers.CardStatusMapper;
import com.processing.cardmanagement.models.Card;
import com.processing.cardmanagement.services.CardService;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import com.processing.common.dto.cardmanagement.CreateCardRequest;
import com.processing.common.dto.cardmanagement.ReserveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardControllerTest {

    private static final String PAN = "4000001234567890";
    private static final String BIN = "400000";

    @Mock
    private CardService cardService;

    @Mock
    private CardRestMapper restMapper;

    @Mock
    private CardStatusMapper cardStatusMapper;

    private CardController cardController;

    @BeforeEach
    void setUp() {
        cardController = new CardController(cardService, restMapper, cardStatusMapper);
    }

    @Test
    void shouldCreateCardAndReturnCreatedStatus() {
        // Arrange
        CreateCardRequest request = createCardRequest();
        Card domainCard = mock(Card.class);
        CardModel createdCard = activeCardModel(new BigDecimal("2000"));

        when(cardService.createCard(
                request.bin(), request.cardholderName(), request.currencyCode(),
                request.dailyLimit(), request.monthlyLimit(), request.initialBalance()
        )).thenReturn(domainCard);
        when(restMapper.toDto(domainCard)).thenReturn(createdCard);

        // Act
        ResponseEntity<CardModel> response = cardController.createCard(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(createdCard);
    }

    @Test
    void shouldReserveFundsSuccessfully() {
        // Arrange
        ReserveRequest request = new ReserveRequest(new BigDecimal("300"), "123456789012");
        Card domainCard = mock(Card.class);
        CardModel cardModel = activeCardModel(new BigDecimal("1700"));

        when(cardService.reserve(PAN, request.amount(), request.rrn())).thenReturn(domainCard);
        when(restMapper.toDto(domainCard)).thenReturn(cardModel);

        // Act
        ResponseEntity<CardModel> response = cardController.reserve(PAN, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(cardModel);
        verify(cardService).reserve(PAN, request.amount(), request.rrn());
    }

    @Test
    void shouldDeleteCardSuccessfully() {
        // Act
        ResponseEntity<Void> response = cardController.deleteCard(PAN);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(cardService).deleteCard(PAN);
    }

    private CreateCardRequest createCardRequest() {
        return new CreateCardRequest(
                BIN, "IVAN IVANOV", "643",
                new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("2000")
        );
    }

    private CardModel activeCardModel(BigDecimal balance) {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                YearMonth.now().plusYears(3), CardModelStatus.ACTIVE, "643",
                new BigDecimal("1000"), new BigDecimal("5000"), balance,
                "BANK01", Instant.now()
        );
    }
}

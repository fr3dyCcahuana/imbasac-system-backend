package com.paulfernandosr.possystembackend.salev2.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paulfernandosr.possystembackend.common.infrastructure.GlobalExceptionHandler;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.exception.SaleCreateRejectedException;
import com.paulfernandosr.possystembackend.salev2.domain.exception.SaleIdempotencyConflictException;
import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleCreateIdempotencyRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreateIdempotentSaleV2ServiceTest {
    private final CreateSaleV2UseCase createSale = mock(CreateSaleV2UseCase.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SaleCreateIdempotencyRepository repository = mock(SaleCreateIdempotencyRepository.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CreateIdempotentSaleV2Service service =
            new CreateIdempotentSaleV2Service(createSale, users, repository, mapper);

    @BeforeEach
    void userExists() {
        when(users.findByUsername("seller")).thenReturn(Optional.of(new User(7L)));
    }

    @Test
    void legacyRequestUsesExistingPathWithoutDependingOnMigration() {
        SaleV2CreateRequest request = request();
        request.setIdempotencyKey(null);
        when(createSale.create(request, "seller")).thenReturn(document());
        assertThat(service.create(request, "seller").getSaleId()).isEqualTo(31L);
        verifyNoInteractions(repository, users);
    }

    @Test
    void validatesUuidBeforeClaimingOrCreatingAnything() {
        for (String invalid : List.of("", "broken", "1-1-1-1-1", " " + UUID.randomUUID())) {
            SaleV2CreateRequest request = request();
            request.setIdempotencyKey(invalid);
            assertThatThrownBy(() -> service.create(request, "seller"))
                    .isInstanceOf(InvalidSaleV2Exception.class);
        }
        verifyNoInteractions(repository, createSale, users);
    }

    @Test
    void savesOriginalResponseAndDoesNotLetSaleNormalizationChangeTheRequest() throws Exception {
        SaleV2CreateRequest request = request();
        String initialHash = service.requestHash(request);
        when(repository.acquire(eq(7L), any(UUID.class), eq(initialHash)))
                .thenReturn(new SaleCreateIdempotencyRepository.Entry(initialHash, null));
        when(createSale.create(any(), eq("seller"))).thenAnswer(invocation -> {
            SaleV2CreateRequest normalized = invocation.getArgument(0);
            normalized.setSeries("S001");
            normalized.setItems(List.of());
            return document();
        });

        assertThat(service.create(request, "seller").getSaleId()).isEqualTo(31L);
        assertThat(request.getSeries()).isNull();
        assertThat(request.getItems()).hasSize(1);
        assertThat(service.requestHash(request)).isEqualTo(initialHash);
        verify(repository).complete(7L, UUID.fromString(request.getIdempotencyKey()), 31L,
                mapper.writeValueAsString(document()));
    }

    @Test
    void replayReturnsOriginalDocumentWithoutRevalidatingStockSessionOrSourceProforma() throws Exception {
        SaleV2CreateRequest request = request();
        request.setSourceProformaNumber(186L);
        request.setSaleSessionId(99L);
        String hash = service.requestHash(request);
        when(repository.acquire(7L, UUID.fromString(request.getIdempotencyKey()), hash))
                .thenReturn(new SaleCreateIdempotencyRepository.Entry(hash, mapper.writeValueAsString(document())));

        assertThat(service.create(request, "seller")).usingRecursiveComparison().isEqualTo(document());
        verifyNoInteractions(createSale);
        verify(repository, never()).complete(any(), any(), any(), any());
    }

    @Test
    void changedPayloadProducesFriendlyConflictWithoutCreatingAnotherSale() {
        SaleV2CreateRequest request = request();
        when(repository.acquire(eq(7L), any(), anyString()))
                .thenReturn(new SaleCreateIdempotencyRepository.Entry("another-hash", "{}"));
        assertThatThrownBy(() -> service.create(request, "seller"))
                .isInstanceOf(SaleIdempotencyConflictException.class).hasMessageContaining("otros datos");
        verifyNoInteractions(createSale);
        verify(repository, never()).complete(any(), any(), any(), any());
    }

    @Test
    void canonicalHashIgnoresKeyAndDecimalScaleButIncludesBusinessChanges() {
        SaleV2CreateRequest first = request();
        SaleV2CreateRequest second = request();
        second.getItems().getFirst().setQuantity(new BigDecimal("2.000"));
        assertThat(service.requestHash(second)).isEqualTo(service.requestHash(first));
        second.getItems().getFirst().setQuantity(new BigDecimal("3"));
        assertThat(service.requestHash(second)).isNotEqualTo(service.requestHash(first));
        second = request();
        second.setSourceProformaNumber(186L);
        assertThat(service.requestHash(second)).isNotEqualTo(service.requestHash(first));
    }

    @Test
    void onlyKnownSaleBusinessRejectionsReceiveTheDefinitiveRejectionMarker() {
        SaleV2CreateRequest request = request();
        String hash = service.requestHash(request);
        when(repository.acquire(eq(7L), any(), anyString()))
                .thenReturn(new SaleCreateIdempotencyRepository.Entry(hash, null));
        when(createSale.create(any(), anyString())).thenThrow(new StockReservationException("Faltan 2 unidades"));
        assertThatThrownBy(() -> service.create(request, "seller"))
                .isInstanceOfSatisfying(SaleCreateRejectedException.class, rejection -> {
                    assertThat(rejection.getIdempotencyKey().toString()).isEqualTo(request.getIdempotencyKey());
                    assertThat(rejection).hasMessage("Faltan 2 unidades");
                });
        verify(repository, never()).complete(any(), any(), any(), any());
    }

    @Test
    void databaseFailuresStayAmbiguousAndNeverReceiveBusinessRejectionMarker() {
        SaleV2CreateRequest request = request();
        String hash = service.requestHash(request);
        when(repository.acquire(eq(7L), any(), anyString()))
                .thenReturn(new SaleCreateIdempotencyRepository.Entry(hash, null));
        when(createSale.create(any(), anyString())).thenThrow(new DataAccessResourceFailureException("connection lost"));
        assertThatThrownBy(() -> service.create(request, "seller"))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(repository, never()).complete(any(), any(), any(), any());
    }

    @Test
    void httpErrorsSeparateChangedPayloadFromRolledBackBusinessRejection() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(MessageSource.class));
        var conflict = handler.handleSaleIdempotencyConflict(new SaleIdempotencyConflictException("otros datos"));
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getBody().getSaleOperationRejected()).isNull();
        UUID key = UUID.randomUUID();
        var rejection = handler.handleSaleCreateRejected(new SaleCreateRejectedException(key,
                new InvalidSaleV2Exception("Stock insuficiente")));
        assertThat(rejection.getStatusCode().value()).isEqualTo(400);
        assertThat(rejection.getBody().getSaleOperationRejected()).isTrue();
        assertThat(rejection.getBody().getIdempotencyKey()).isEqualTo(key.toString());
    }

    private static SaleV2CreateRequest request() {
        return SaleV2CreateRequest.builder().idempotencyKey(UUID.randomUUID().toString())
                .docType(DocType.SIMPLE).issueDate(LocalDate.of(2026, 10, 22))
                .items(List.of(SaleV2CreateRequest.Item.builder().productId(1L).quantity(new BigDecimal("2")).build()))
                .build();
    }

    private static SaleV2DocumentResponse document() {
        return SaleV2DocumentResponse.builder().saleId(31L).docType(DocType.SIMPLE).series("S001").number(98L)
                .issueDate(LocalDate.of(2026, 10, 22)).total(new BigDecimal("14.2500")).build();
    }
}

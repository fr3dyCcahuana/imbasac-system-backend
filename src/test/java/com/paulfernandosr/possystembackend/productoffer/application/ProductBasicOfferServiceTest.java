package com.paulfernandosr.possystembackend.productoffer.application;

import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.ProductImagePublicUrlService;
import com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input.dto.ProductBasicOfferItemRequest;
import com.paulfernandosr.possystembackend.productoffer.infrastructure.adapter.input.dto.ProductBasicOfferRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class ProductBasicOfferServiceTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsUnavailableProductsOnCreateAndUpdate(boolean editing) {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec header = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.MappedQuerySpec<Long> ids = mock(JdbcClient.MappedQuerySpec.class);
        JdbcClient.StatementSpec delete = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.StatementSpec insert = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        when(jdbc.sql(startsWith("INSERT INTO product_basic_offer ("))).thenReturn(header);
        when(jdbc.sql(startsWith("UPDATE product_basic_offer"))).thenReturn(header);
        when(header.query(Long.class)).thenReturn(ids);
        when(ids.single()).thenReturn(10L);
        when(header.update()).thenReturn(1);
        when(jdbc.sql("DELETE FROM product_basic_offer_item WHERE offer_id = ?")).thenReturn(delete);
        when(jdbc.sql(startsWith("INSERT INTO product_basic_offer_item"))).thenReturn(insert);
        // The guarded INSERT affects no rows when stock is no longer available.
        when(insert.update()).thenReturn(0);

        ProductBasicOfferService service = spy(new ProductBasicOfferService(
                jdbc, mock(ProductImagePublicUrlService.class)));
        ProductBasicOfferRequest request = new ProductBasicOfferRequest(
                "Oferta", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 27), "ACTIVE",
                List.of(new ProductBasicOfferItemRequest(1L, BigDecimal.ONE, BigDecimal.TEN, 0)));

        assertThatThrownBy(() -> {
            if (editing) service.update(10L, request);
            else service.create(request);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene stock disponible");

        verify(service, never()).findById(any());
        verify(insert).update();
    }
}

package com.paulfernandosr.possystembackend.product.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleItemResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductJsonVisibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void productDoesNotExposeSunatProductCodeInJson() throws Exception {
        Product product = Product.builder()
                .id(1L)
                .sku("SKU-1")
                .name("Producto test")
                .sunatProductCode("25101801")
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(product));

        assertThat(json.has("sunatProductCode")).isFalse();
    }

    @Test
    void productIgnoresSunatProductCodeFromJson() throws Exception {
        Product product = objectMapper.readValue(
                """
                {
                  "sku": "SKU-1",
                  "name": "Producto test",
                  "sunatProductCode": "25101801"
                }
                """,
                Product.class
        );

        assertThat(product.getSunatProductCode()).isNull();
    }

    @Test
    void counterSaleItemDoesNotExposeSunatProductCodeInJson() throws Exception {
        CounterSaleItemResponse item = CounterSaleItemResponse.builder()
                .counterSaleItemId(1L)
                .sku("SKU-1")
                .description("Producto test")
                .sunatProductCode("25101801")
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(item));

        assertThat(json.has("sunatProductCode")).isFalse();
    }
}

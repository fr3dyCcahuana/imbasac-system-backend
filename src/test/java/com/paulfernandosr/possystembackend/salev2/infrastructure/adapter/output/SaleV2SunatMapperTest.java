package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.infrastructure.sunat.SunatProductCodeValidator;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2SunatRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleV2SunatMapperTest {

    @Test
    void usesConfiguredSunatProductCodeWhenItExistsInCatalog25() {
        DocumentRequest request = SaleV2SunatMapper.map(
                props(),
                sale(),
                List.of(item("25101801"))
        );

        DocumentRequest.Item mapped = request.getItems().iterator().next();
        assertThat(mapped.getSunatCode()).isEqualTo("25101801");
        assertThat(mapped.getProductCode()).isEqualTo("SKU-1");
    }

    @Test
    void rejectsConfiguredSunatProductCodeWithInvalidFormatBeforeSendingToSunat() {
        assertThatThrownBy(() -> SaleV2SunatMapper.map(
                props(),
                sale(),
                List.of(item("ABC"))
        ))
                .isInstanceOf(InvalidSaleV2Exception.class)
                .hasMessageContaining("Codigo Producto SUNAT");
    }

    @Test
    void catalog25ValidationRejectsOldGenericFallback() {
        assertThat(SunatProductCodeValidator.isValid("25101801")).isTrue();
        assertThat(SunatProductCodeValidator.isValid("01010101")).isFalse();
    }

    private static SunatProps props() {
        SunatProps props = new SunatProps();
        props.setMode("0");
        SunatProps.Business business = new SunatProps.Business();
        business.setRuc("20600000000");
        business.setBusinessName("IMBASAC TEST");
        business.setTradeName("IMBASAC");
        business.setTaxAddress("LIMA");
        business.setUbigeo("150101");
        business.setNeighborhood("-");
        business.setDistrict("LIMA");
        business.setProvince("LIMA");
        business.setDepartment("LIMA");
        props.setBusiness(business);
        return props;
    }

    private static SaleV2SunatRepository.LockedSunatSale sale() {
        return SaleV2SunatRepository.LockedSunatSale.builder()
                .saleId(1L)
                .status("EMITIDA")
                .docType("FACTURA")
                .series("F001")
                .number(1L)
                .issueDate(LocalDate.of(2026, 7, 31))
                .createdAt(LocalDateTime.of(2026, 7, 31, 10, 0))
                .currency("PEN")
                .customerDocType("RUC")
                .customerDocNumber("20123456789")
                .customerName("CLIENTE TEST")
                .customerAddress("LIMA")
                .taxStatus("GRAVADA")
                .subtotal(new BigDecimal("100.00"))
                .discountTotal(BigDecimal.ZERO)
                .igvAmount(new BigDecimal("18.00"))
                .total(new BigDecimal("118.00"))
                .build();
    }

    private static SaleV2SunatRepository.SaleItemForSunat item(String sunatProductCode) {
        return SaleV2SunatRepository.SaleItemForSunat.builder()
                .lineNumber(1)
                .productId(10L)
                .sku("SKU-1")
                .description("PRODUCTO TEST")
                .productCategory("ART GENERAL")
                .sunatProductCode(sunatProductCode)
                .quantity(BigDecimal.ONE)
                .revenueTotal(new BigDecimal("100.00"))
                .lineKind("VENDIDO")
                .visibleInDocument(true)
                .build();
    }
}

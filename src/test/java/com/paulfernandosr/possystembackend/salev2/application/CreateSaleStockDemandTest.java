package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.ProductSnapshot;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateSaleStockDemandTest {
    private final ProductSnapshotRepository products = mock(ProductSnapshotRepository.class);
    private final CreateSaleV2Service service = mock(CreateSaleV2Service.class, CALLS_REAL_METHODS);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "productSnapshotRepository", products);
    }

    @Test
    void aggregatesDuplicateSaleLinesAndAllInternalProformaDemand() throws Exception {
        ProductSnapshot saleProduct = product(100L, true, false);
        when(products.findSnapshotById(200L)).thenReturn(product(200L, true, false));
        List<?> saleLines = List.of(line(saleProduct, "2"), line(saleProduct, "3"));
        List<ProformaItem> sourceItems = List.of(internal(200L, "4"), internal(200L, "1"),
                ProformaItem.builder().productId(100L).quantity(new BigDecimal("5"))
                        .facturableSunat(true).affectsStock(true).build());

        Map<Long, BigDecimal> demand = demand(saleLines, sourceItems);
        assertThat(demand).hasSize(2);
        assertThat(demand.get(100L)).isEqualByComparingTo("5");
        assertThat(demand.get(200L)).isEqualByComparingTo("5");
    }

    @Test
    void includesSerializedSaleProductsInPhysicalStockGuardAndSkipsServices() throws Exception {
        List<?> lines = List.of(line(product(100L, true, true), "2"), line(product(200L, false, false), "99"));
        assertThat(demand(lines, List.of())).containsOnlyKeys(100L);
    }

    @Test
    void internalItemWhoseProductNoLongerAffectsStockDoesNotConsumeStock() {
        when(products.findSnapshotById(200L)).thenReturn(product(200L, false, false));
        assertThat(demand(List.of(), List.of(internal(200L, "4")))).isEmpty();
    }

    @Test
    void rejectsUnsupportedSerializedInternalItemsBeforeAnyStockMovement() {
        when(products.findSnapshotById(200L)).thenReturn(product(200L, true, true));
        assertThatThrownBy(() -> demand(List.of(), List.of(internal(200L, "1"))))
                .isInstanceOf(InvalidSaleV2Exception.class).hasMessageContaining("interno no facturable con serie/VIN");
    }

    private Map<Long, BigDecimal> demand(List<?> lines, List<ProformaItem> internalItems) {
        return ReflectionTestUtils.invokeMethod(service, "collectStockQuantities", lines, internalItems);
    }

    private static Object line(ProductSnapshot product, String quantity) throws ClassNotFoundException {
        Class<?> type = Class.forName(CreateSaleV2Service.class.getName() + "$ComputedLine");
        Object builder = ReflectionTestUtils.invokeMethod(type, "builder");
        ReflectionTestUtils.invokeMethod(builder, "product", product);
        ReflectionTestUtils.invokeMethod(builder, "quantity", new BigDecimal(quantity));
        return ReflectionTestUtils.invokeMethod(builder, "build");
    }

    private static ProductSnapshot product(Long id, boolean affectsStock, boolean serial) {
        return ProductSnapshot.builder().id(id).sku("P" + id).affectsStock(affectsStock).manageBySerial(serial).build();
    }

    private static ProformaItem internal(Long productId, String quantity) {
        return ProformaItem.builder().productId(productId).quantity(new BigDecimal(quantity))
                .facturableSunat(false).affectsStock(true).build();
    }
}

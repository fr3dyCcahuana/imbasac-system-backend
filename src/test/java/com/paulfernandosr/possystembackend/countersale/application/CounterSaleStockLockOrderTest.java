package com.paulfernandosr.possystembackend.countersale.application;

import com.paulfernandosr.possystembackend.countersale.domain.model.*;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.*;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleCreateRequest;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output.CounterSalePostgresProductStockRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CounterSaleStockLockOrderTest {
    @Test
    void locksDistinctStockProductsBeforeAnySaleInsertEvenWithUnsortedRepeatedLines() {
        UserRepository users = mock(UserRepository.class);
        DocumentSeriesRepository series = mock(DocumentSeriesRepository.class);
        ProductSnapshotRepository products = mock(ProductSnapshotRepository.class);
        ProductStockRepository stock = mock(ProductStockRepository.class);
        AtomicBoolean stockLocked = new AtomicBoolean(false);
        CounterSaleRepository sales = mock(CounterSaleRepository.class, invocation -> {
            if (invocation.getMethod().getName().startsWith("insert")) {
                assertThat(stockLocked).as("all product stock locks precede sale/item writes").isTrue();
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        when(users.findByUsername("seller")).thenReturn(Optional.of(User.builder().id(50L).build()));
        when(series.lockSeries("VENTANILLA", "V001"))
                .thenReturn(LockedDocumentSeries.builder().id(1L).nextNumber(1L).build());
        for (Long id : List.of(1L, 2L, 3L)) {
            when(products.findSnapshotById(id)).thenReturn(ProductSnapshot.builder().id(id)
                    .affectsStock(id != 3L).manageBySerial(false).priceA(BigDecimal.ONE).build());
        }
        doAnswer(invocation -> {
            assertThat((List<Long>) invocation.getArgument(0)).containsExactly(1L, 2L);
            stockLocked.set(true);
            return null;
        }).when(stock).lockProducts(anyCollection());
        when(stock.decreaseOnHandOrFail(anyLong(), any())).thenReturn(StockMovementBalance.builder()
                .quantityOnHand(BigDecimal.TEN).averageCost(BigDecimal.ZERO).lastUnitCost(BigDecimal.ZERO).build());

        CreateCounterSaleService service = new CreateCounterSaleService(users, series, products, sales,
                mock(CounterSalePaymentRepository.class), stock, mock(ProductStockMovementRepository.class),
                mock(ProductSerialUnitRepository.class), mock(SaleSessionControlRepository.class),
                mock(SaleSessionAccumulatorRepository.class));
        CounterSaleCreateRequest request = CounterSaleCreateRequest.builder().stationId(1L).series("V001")
                .priceList(CounterSalePriceList.A).customerDocType("GEN")
                .items(List.of(item(2L), item(3L), item(1L), item(2L)))
                .payment(CounterSaleCreateRequest.Payment.builder().method(CounterSalePaymentMethod.EFECTIVO).build())
                .build();

        service.create(request, "seller");

        var order = inOrder(stock);
        order.verify(stock).lockProducts(List.of(1L, 2L));
        order.verify(stock).decreaseOnHandOrFail(2L, BigDecimal.ONE);
        order.verify(stock).decreaseOnHandOrFail(1L, BigDecimal.ONE);
        order.verify(stock).decreaseOnHandOrFail(2L, BigDecimal.ONE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void repositoryLocksAscendingAndDoesNotLockRepeatedRowsTwice() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.MappedQuerySpec<BigDecimal> query = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql("SELECT quantity_on_hand FROM product_stock WHERE product_id = ? FOR UPDATE"))
                .thenReturn(statement);
        when(statement.query(BigDecimal.class)).thenReturn(query);
        when(query.optional()).thenReturn(Optional.of(BigDecimal.TEN));

        new CounterSalePostgresProductStockRepository(jdbc).lockProducts(List.of(9L, 2L, 9L, 1L));

        ArgumentCaptor<Object> ids = ArgumentCaptor.forClass(Object.class);
        verify(statement, times(3)).param(ids.capture());
        assertThat(ids.getAllValues()).containsExactly(1L, 2L, 9L);
    }

    private static CounterSaleCreateRequest.Item item(Long productId) {
        return CounterSaleCreateRequest.Item.builder().productId(productId).quantity(BigDecimal.ONE).build();
    }
}

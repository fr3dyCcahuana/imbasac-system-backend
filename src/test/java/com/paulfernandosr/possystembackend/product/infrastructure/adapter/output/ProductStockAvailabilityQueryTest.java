package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductStockValidationDto;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProductStockAvailabilityQueryTest {

    @Test
    void validationExposesOtherProformasAndKeepsMissingIdsInRequestOrder() throws Exception {
        QueryFixture fixture = new QueryFixture();
        fixture.products.add(product(8L, true, false, "10", "7", "2", "3"));
        Map<String, Object> reservation = new HashMap<>();
        reservation.put("product_id", 8L);
        reservation.put("reservation_id", 72L);
        reservation.put("proforma_id", 100L);
        reservation.put("proforma_series", "PF01");
        reservation.put("proforma_number", 25L);
        reservation.put("reserved_by", 4L);
        reservation.put("reserved_by_name", "Carlos Pérez");
        reservation.put("quantity", new BigDecimal("7"));
        LocalDateTime expiry = LocalDateTime.of(2026, 10, 23, 0, 0);
        reservation.put("expires_at", Timestamp.valueOf(expiry));
        fixture.reservations.add(reservation);

        List<ProductStockValidationDto> result = new ArrayList<>(new PostgresProductStockValidationRepository(fixture.jdbc)
                .validate(Arrays.asList(8L, null, 99L, 8L), false, 50, 40L));

        assertThat(result).extracting(ProductStockValidationDto::getId).containsExactly(8L, 99L);
        ProductStockValidationDto found = result.getFirst();
        assertThat(found.getStockOnHand()).isEqualByComparingTo("10");
        assertThat(found.getStockAvailable()).isEqualByComparingTo("3");
        assertThat(found.getStockReserved()).isEqualByComparingTo("7");
        assertThat(found.getStockReservedBySource()).isEqualByComparingTo("2");
        assertThat(found.getStockReservations()).singleElement().satisfies(detail -> {
            assertThat(detail.getReservationId()).isEqualTo(72L);
            assertThat(detail.getProformaId()).isEqualTo(100L);
            assertThat(detail.getProformaSeries()).isEqualTo("PF01");
            assertThat(detail.getProformaNumber()).isEqualTo(25L);
            assertThat(detail.getReservedBy()).isEqualTo(4L);
            assertThat(detail.getReservedByName()).isEqualTo("Carlos Pérez");
            assertThat(detail.getQuantity()).isEqualByComparingTo("7");
            assertThat(detail.getExpiresAt()).isEqualTo(expiry);
        });
        assertThat(result.getLast().isExists()).isFalse();
        assertThat(result.getLast().getStockOnHand()).isZero();
        assertThat(result.getLast().getStockReservations()).isEmpty();
        assertThat(fixture.calls).hasSize(2);
        assertThat(fixture.calls.getFirst().params).containsExactly(40L, 8L, 99L);
        assertThat(fixture.calls.getLast().params).containsExactly(40L, 8L);
        assertThat(fixture.calls.getLast().sql).contains("r.proforma_id IS DISTINCT FROM", "r.status = 'ACTIVE'",
                "r.reserved_date = CURRENT_DATE");
        fixture.assertBindingsMatch();
    }

    @Test
    void nonStockProductsSerialsAndUnavailableProductsKeepTheirExistingSemantics() throws Exception {
        QueryFixture fixture = new QueryFixture();
        fixture.products.add(product(1L, false, false, "0", "0", "0", "0"));
        fixture.products.add(product(2L, true, true, "3", "0", "0", "3"));
        fixture.products.add(product(3L, true, false, "5", "5", "0", "0"));

        List<ProductStockValidationDto> result = new ArrayList<>(new PostgresProductStockValidationRepository(fixture.jdbc)
                .validate(List.of(1L, 2L, 3L), false, 50, null));

        assertThat(result.get(0).isInStock()).isTrue();
        assertThat(result.get(1).isInStock()).isTrue();
        assertThat(result.get(1).getStockReservations()).isEmpty();
        assertThat(result.get(1).getStockOnHand()).isEqualByComparingTo("3");
        assertThat(result.get(2).isInStock()).isFalse();
        assertThat(fixture.calls.getLast().params).containsExactly(null, 3L);
        fixture.assertBindingsMatch();
    }

    @Test
    void emptyValidationDoesNotQueryDatabase() throws Exception {
        QueryFixture fixture = new QueryFixture();
        assertThat(new PostgresProductStockValidationRepository(fixture.jdbc)
                .validate(null, true, 50, null)).isEmpty();
        verifyNoInteractions(fixture.jdbc);
    }

    @Test
    void saleAndProformaCatalogUseTheSameProjectionWithDifferentStockFilters() throws Exception {
        for (String context : List.of("SALE", "PROFORMA")) {
            QueryFixture fixture = new QueryFixture();
            new PostgresProductSalesDetailRepository(fixture.jdbc).findPage(
                    "aceite", "", "", "", true, "B", context, 40L, new Pageable(0, 12));

            assertThat(fixture.calls).hasSize(2);
            for (QueryCall call : fixture.calls) {
                assertThat(call.sql).contains("THEN stock_on_hand ELSE stock_available END > 0",
                        "reserved_qty", "source_reserved_qty");
                assertThat(call.params).containsSubsequence(true, context);
                assertThat(call.params.getFirst()).isEqualTo(40L);
            }
            assertThat(fixture.calls.getLast().params.get(1)).isEqualTo("B");
            fixture.assertBindingsMatch();
        }
    }

    private static Map<String, Object> product(Long id, boolean affectsStock, boolean serial,
                                               String onHand, String reserved, String own, String available) {
        return Map.of("id", id, "affects_stock", affectsStock, "manage_by_serial", serial,
                "stock_on_hand", new BigDecimal(onHand), "stock_reserved", new BigDecimal(reserved),
                "stock_reserved_by_source", new BigDecimal(own), "stock_available", new BigDecimal(available));
    }

    private static class QueryCall {
        final String sql;
        List<Object> params = List.of();

        QueryCall(String sql) {
            this.sql = sql;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static class QueryFixture {
        final JdbcClient jdbc = mock(JdbcClient.class);
        final List<QueryCall> calls = new ArrayList<>();
        final List<Map<String, Object>> products = new ArrayList<>();
        final List<Map<String, Object>> reservations = new ArrayList<>();

        QueryFixture() throws Exception {
            when(jdbc.sql(anyString())).thenAnswer(invocation -> {
                QueryCall call = new QueryCall(invocation.getArgument(0));
                calls.add(call);
                JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
                doAnswer(binding -> {
                    call.params = Arrays.asList((Object[]) binding.getRawArguments()[0]);
                    return statement;
                }).when(statement).params(any(Object[].class));
                when(statement.query(any(RowMapper.class))).thenAnswer(mapping -> {
                    RowMapper mapper = mapping.getArgument(0);
                    List<Map<String, Object>> rows = call.sql.stripLeading().startsWith("SELECT r.product_id") ? reservations : products;
                    List<Object> mapped = new ArrayList<>();
                    for (int i = 0; i < rows.size(); i++) {
                        Map<String, Object> row = rows.get(i);
                        ResultSet resultSet = mock(ResultSet.class);
                        when(resultSet.getObject(anyString())).thenAnswer(field -> row.get(field.getArgument(0)));
                        when(resultSet.getLong(anyString())).thenAnswer(field -> ((Number) row.getOrDefault(field.getArgument(0), 0L)).longValue());
                        when(resultSet.getBigDecimal(anyString())).thenAnswer(field -> row.get(field.getArgument(0)));
                        when(resultSet.getString(anyString())).thenAnswer(field -> row.get(field.getArgument(0)));
                        when(resultSet.getTimestamp(anyString())).thenAnswer(field -> row.get(field.getArgument(0)));
                        mapped.add(mapper.mapRow(resultSet, i));
                    }
                    JdbcClient.MappedQuerySpec result = mock(JdbcClient.MappedQuerySpec.class);
                    when(result.list()).thenReturn(mapped);
                    return result;
                });
                JdbcClient.MappedQuerySpec<Long> count = mock(JdbcClient.MappedQuerySpec.class);
                when(count.single()).thenReturn(0L);
                when(statement.query(Long.class)).thenReturn(count);
                return statement;
            });
        }

        void assertBindingsMatch() {
            for (QueryCall call : calls) {
                assertThat(call.params).hasSize((int) call.sql.chars().filter(character -> character == '?').count());
                assertThat(call.sql).doesNotContain("WHERE(");
            }
        }
    }
}

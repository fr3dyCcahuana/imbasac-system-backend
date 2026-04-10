package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2CounterSaleCompositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresSaleV2CounterSaleCompositionRepository implements SaleV2CounterSaleCompositionRepository {

    private final JdbcClient jdbcClient;

    @Override
    public List<LockedCounterSaleForComposition> lockCounterSales(List<Long> counterSaleIds) {
        if (counterSaleIds == null || counterSaleIds.isEmpty()) {
            return List.of();
        }
        String placeholders = counterSaleIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("""
            SELECT cs.id,
                   cs.series,
                   cs.number,
                   cs.status,
                   cs.associated_to_sunat,
                   cs.associated_sale_id,
                   cs.associated_doc_type,
                   cs.associated_series,
                   cs.associated_number,
                   cs.total,
                   cs.discount_total,
                   cs.associated_at
              FROM counter_sale cs
             WHERE cs.id IN (%s)
             FOR UPDATE
        """, placeholders);
        return jdbcClient.sql(sql)
                .params(counterSaleIds.toArray())
                .query((rs, rowNum) -> LockedCounterSaleForComposition.builder()
                        .id(rs.getLong("id"))
                        .series(rs.getString("series"))
                        .number((Long) rs.getObject("number"))
                        .status(rs.getString("status"))
                        .associatedToSunat(rs.getObject("associated_to_sunat", Boolean.class))
                        .associatedSaleId((Long) rs.getObject("associated_sale_id"))
                        .associatedDocType(rs.getString("associated_doc_type"))
                        .associatedSeries(rs.getString("associated_series"))
                        .associatedNumber((Long) rs.getObject("associated_number"))
                        .total(rs.getBigDecimal("total"))
                        .discountTotal(rs.getBigDecimal("discount_total"))
                        .associatedAt(rs.getTimestamp("associated_at") != null ? rs.getTimestamp("associated_at").toLocalDateTime() : null)
                        .build())
                .list();
    }

    @Override
    public void reserveCounterSale(Long saleId,
                                   Long counterSaleId,
                                   BigDecimal counterSaleTotal,
                                   BigDecimal counterSaleDiscountTotal,
                                   Long reservedBy,
                                   String reservedByUsername,
                                   String editReason) {
        String sql = """
            INSERT INTO sale_counter_sale_sunat_link(
                sale_id,
                counter_sale_id,
                counter_sale_total,
                counter_sale_discount_total,
                reservation_status,
                reserved_by,
                reserved_by_username,
                edit_reason,
                reserved_at
            ) VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, NOW())
        """;
        jdbcClient.sql(sql)
                .params(saleId, counterSaleId, counterSaleTotal, counterSaleDiscountTotal, reservedBy, reservedByUsername, editReason)
                .update();
    }

    @Override
    public void releaseCounterSales(Long saleId, List<Long> counterSaleIds, String releaseReason) {
        if (counterSaleIds == null || counterSaleIds.isEmpty()) {
            return;
        }
        String placeholders = counterSaleIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("""
            UPDATE sale_counter_sale_sunat_link
               SET reservation_status = 'LIBERADO',
                   release_reason = ?,
                   released_at = NOW(),
                   updated_at = NOW()
             WHERE sale_id = ?
               AND counter_sale_id IN (%s)
               AND reservation_status = 'PENDING'
        """, placeholders);
        Object[] params = new Object[counterSaleIds.size() + 2];
        params[0] = releaseReason;
        params[1] = saleId;
        for (int i = 0; i < counterSaleIds.size(); i++) {
            params[i + 2] = counterSaleIds.get(i);
        }
        jdbcClient.sql(sql).params(params).update();
    }

    @Override
    public void finalizeAcceptedCounterSale(Long saleId,
                                            Long counterSaleId,
                                            String emittedDocType,
                                            String emittedSeries,
                                            Long emittedNumber) {
        String updateLink = """
            UPDATE sale_counter_sale_sunat_link
               SET reservation_status = 'ACEPTADO',
                   emitted_doc_type = ?,
                   emitted_series = ?,
                   emitted_number = ?,
                   associated_at = NOW(),
                   updated_at = NOW()
             WHERE sale_id = ?
               AND counter_sale_id = ?
               AND reservation_status = 'PENDING'
        """;
        jdbcClient.sql(updateLink)
                .params(emittedDocType, emittedSeries, emittedNumber, saleId, counterSaleId)
                .update();

        String updateCounterSale = """
            UPDATE counter_sale
               SET associated_to_sunat = TRUE,
                   associated_sale_id = ?,
                   associated_doc_type = ?,
                   associated_series = ?,
                   associated_number = ?,
                   associated_at = NOW(),
                   updated_at = NOW()
             WHERE id = ?
               AND status = 'EMITIDA'
               AND COALESCE(associated_to_sunat, FALSE) = FALSE
        """;
        jdbcClient.sql(updateCounterSale)
                .params(saleId, emittedDocType, emittedSeries, emittedNumber, counterSaleId)
                .update();
    }

    @Override
    public void insertAcceptedCounterSaleItem(Long saleId,
                                              Long counterSaleId,
                                              Long counterSaleItemId,
                                              Integer sourceLineNumber,
                                              Long productId,
                                              String sku,
                                              String description,
                                              BigDecimal quantity,
                                              BigDecimal originalUnitPrice,
                                              BigDecimal emittedUnitPrice,
                                              BigDecimal originalRevenueTotal,
                                              BigDecimal emittedRevenueTotal) {
        String sql = """
            INSERT INTO sale_counter_sale_sunat_link_item(
                sale_id,
                counter_sale_id,
                counter_sale_item_id,
                source_line_number,
                product_id,
                sku,
                description,
                quantity,
                original_unit_price,
                emitted_unit_price,
                original_revenue_total,
                emitted_revenue_total,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """;
        jdbcClient.sql(sql)
                .params(saleId, counterSaleId, counterSaleItemId, sourceLineNumber, productId, sku, description,
                        quantity, originalUnitPrice, emittedUnitPrice, originalRevenueTotal, emittedRevenueTotal)
                .update();
    }
}

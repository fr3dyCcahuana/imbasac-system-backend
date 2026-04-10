package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.port.output.CounterSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresRepository implements CounterSaleRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Long insertCounterSale(Long stationId, Long saleSessionId, Long createdBy, String series, Long number,
                                  LocalDate issueDate, String currency, BigDecimal exchangeRate, String priceList,
                                  Long customerId, String customerDocType, String customerDocNumber,
                                  String customerName, String customerAddress, String taxStatus,
                                  BigDecimal igvRate, Boolean igvIncluded, String notes) {
        String sql = """
            INSERT INTO counter_sale(
              station_id, sale_session_id, created_by,
              series, number, issue_date,
              currency, exchange_rate, price_list,
              customer_id, customer_doc_type, customer_doc_number, customer_name, customer_address,
              tax_status, igv_rate, igv_included,
              subtotal, discount_total, igv_amount, total, gift_cost_total,
              notes, status
            ) VALUES (
              ?, ?, ?,
              ?, ?, ?,
              ?, ?, ?,
              ?, ?, ?, ?, ?,
              ?, ?, ?,
              0, 0, 0, 0, 0,
              ?, 'EMITIDA'
            )
            RETURNING id
        """;
        return jdbcClient.sql(sql)
                .params(
                        stationId, saleSessionId, createdBy,
                        series, number, issueDate,
                        currency, exchangeRate, priceList,
                        customerId, customerDocType, customerDocNumber, customerName, customerAddress,
                        taxStatus, igvRate, igvIncluded != null ? igvIncluded : Boolean.FALSE,
                        notes
                )
                .query(Long.class)
                .single();
    }

    @Override
    public Long insertCounterSaleItem(Long counterSaleId, Integer lineNumber, Long productId, String sku,
                                      String description, String presentation, BigDecimal factor, BigDecimal quantity,
                                      BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal discountAmount,
                                      String lineKind, String giftReason, Boolean affectsStock,
                                      BigDecimal unitCostSnapshot, BigDecimal totalCostSnapshot, BigDecimal revenueTotal) {
        String sql = """
            INSERT INTO counter_sale_item(
              counter_sale_id, line_number,
              product_id, sku, description, presentation, factor,
              quantity, unit_price, discount_percent, discount_amount,
              line_kind, gift_reason, affects_stock,
              unit_cost_snapshot, total_cost_snapshot, revenue_total
            ) VALUES (
              ?, ?,
              ?, ?, ?, ?, ?,
              ?, ?, ?, ?,
              ?, ?, ?,
              ?, ?, ?
            )
            RETURNING id
        """;
        return jdbcClient.sql(sql)
                .params(
                        counterSaleId, lineNumber,
                        productId, sku, description, presentation, factor,
                        quantity, unitPrice, discountPercent, discountAmount,
                        lineKind, giftReason, affectsStock,
                        unitCostSnapshot, totalCostSnapshot, revenueTotal
                )
                .query(Long.class)
                .single();
    }

    @Override
    public void linkSerialUnit(Long counterSaleItemId, Long serialUnitId) {
        jdbcClient.sql("INSERT INTO counter_sale_serial_unit(counter_sale_item_id, serial_unit_id) VALUES (?, ?)")
                .params(counterSaleItemId, serialUnitId)
                .update();
    }

    @Override
    public void updateTotals(Long counterSaleId, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal igvAmount,
                             BigDecimal total, BigDecimal giftCostTotal) {
        String sql = """
            UPDATE counter_sale
               SET subtotal = ?,
                   discount_total = ?,
                   igv_amount = ?,
                   total = ?,
                   gift_cost_total = ?,
                   updated_at = NOW()
             WHERE id = ?
        """;
        jdbcClient.sql(sql)
                .params(subtotal, discountTotal, igvAmount, total, giftCostTotal, counterSaleId)
                .update();
    }

    @Override
    public LockedCounterSale lockById(Long counterSaleId) {
        String sql = """
            SELECT id, sale_session_id, customer_id, status, total, discount_total
              FROM counter_sale
             WHERE id = ?
             FOR UPDATE
        """;
        return jdbcClient.sql(sql)
                .param(counterSaleId)
                .query((rs, rowNum) -> LockedCounterSale.builder()
                        .id(rs.getLong("id"))
                        .saleSessionId((Long) rs.getObject("sale_session_id"))
                        .customerId((Long) rs.getObject("customer_id"))
                        .status(rs.getString("status"))
                        .total(rs.getBigDecimal("total"))
                        .discountTotal(rs.getBigDecimal("discount_total"))
                        .build())
                .optional()
                .orElse(null);
    }

    @Override
    public List<CounterSaleItemForVoid> findItemsByCounterSaleId(Long counterSaleId) {
        String sql = """
            SELECT id,
                   line_number,
                   product_id,
                   sku,
                   description,
                   presentation,
                   factor,
                   quantity,
                   unit_price,
                   discount_percent,
                   discount_amount,
                   line_kind,
                   gift_reason,
                   affects_stock,
                   revenue_total,
                   unit_cost_snapshot,
                   total_cost_snapshot
              FROM counter_sale_item
             WHERE counter_sale_id = ?
             ORDER BY line_number
        """;
        return jdbcClient.sql(sql)
                .param(counterSaleId)
                .query((rs, rowNum) -> CounterSaleItemForVoid.builder()
                        .id(rs.getLong("id"))
                        .lineNumber((Integer) rs.getObject("line_number"))
                        .productId(rs.getLong("product_id"))
                        .sku(rs.getString("sku"))
                        .description(rs.getString("description"))
                        .presentation(rs.getString("presentation"))
                        .factor(rs.getBigDecimal("factor"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .unitPrice(rs.getBigDecimal("unit_price"))
                        .discountPercent(rs.getBigDecimal("discount_percent"))
                        .discountAmount(rs.getBigDecimal("discount_amount"))
                        .lineKind(rs.getString("line_kind"))
                        .giftReason(rs.getString("gift_reason"))
                        .affectsStock(rs.getObject("affects_stock", Boolean.class))
                        .revenueTotal(rs.getBigDecimal("revenue_total"))
                        .unitCostSnapshot(rs.getBigDecimal("unit_cost_snapshot"))
                        .totalCostSnapshot(rs.getBigDecimal("total_cost_snapshot"))
                        .build())
                .list();
    }

    @Override
    public void markAsVoided(Long counterSaleId, Long voidedBy, String voidReason) {
        String sql = """
            UPDATE counter_sale
               SET status = 'ANULADA',
                   voided_at = NOW(),
                   voided_by = ?,
                   void_reason = ?,
                   notes = CASE
                             WHEN notes IS NULL OR notes = '' THEN CONCAT('ANULADA: ', ?)
                             ELSE notes || E'
' || CONCAT('ANULADA: ', ?)
                           END,
                   updated_at = NOW()
             WHERE id = ?
        """;
        jdbcClient.sql(sql)
                .params(voidedBy, voidReason, voidReason, voidReason, counterSaleId)
                .update();
    }
}

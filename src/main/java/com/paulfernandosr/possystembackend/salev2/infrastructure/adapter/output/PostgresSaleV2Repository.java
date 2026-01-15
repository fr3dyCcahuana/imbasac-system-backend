package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresSaleV2Repository implements SaleV2Repository {

    private final JdbcClient jdbcClient;

    @Override
    public Long insertSale(Long stationId, Long saleSessionId, Long createdBy, String docType, String series, Long number,
                           LocalDate issueDate, String currency, BigDecimal exchangeRate, String priceList,
                           Long customerId, String customerDocType, String customerDocNumber, String customerName,
                           String customerAddress, String taxStatus, String taxReason, BigDecimal igvRate, Boolean igvIncluded,
                           String paymentType, Integer creditDays, LocalDate dueDate, String notes) {

        String sql = """
                    INSERT INTO sale(
                      station_id, sale_session_id, created_by,
                      doc_type, series, number, issue_date,
                      currency, exchange_rate,
                      price_list,
                      customer_id, customer_doc_type, customer_doc_number, customer_name, customer_address,
                      tax_status, tax_reason, igv_rate, igv_included,
                      payment_type, credit_days, due_date,
                      subtotal, discount_total, igv_amount, total, gift_cost_total,
                      notes, status
                    ) VALUES (
                      ?, ?, ?,
                      ?, ?, ?, ?,
                      ?, ?,
                      ?,
                      ?, ?, ?, ?, ?,
                      ?, ?, ?, ?,
                      ?, ?, ?,
                      0, 0, 0, 0, 0,
                      ?, 'EMITIDA'
                    )
                    RETURNING id
                """;

        return jdbcClient.sql(sql)
                .params(
                        stationId, saleSessionId, createdBy,
                        docType, series, number, issueDate,
                        currency, exchangeRate,
                        priceList,
                        customerId, customerDocType, customerDocNumber, customerName, customerAddress,
                        taxStatus, taxReason, igvRate, (igvIncluded != null ? igvIncluded : Boolean.FALSE),
                        paymentType, creditDays, dueDate,
                        notes
                )
                .query(Long.class)
                .single();
    }

    @Override
    public Long insertSaleItem(Long saleId, Integer lineNumber, Long productId, String sku, String description,
                               String presentation, BigDecimal factor, BigDecimal quantity, BigDecimal unitPrice,
                               BigDecimal discountPercent, BigDecimal discountAmount, String lineKind, String giftReason,
                               Boolean facturableSunat, Boolean affectsStock, Boolean visibleInDocument,
                               BigDecimal unitCostSnapshot, BigDecimal totalCostSnapshot, BigDecimal revenueTotal) {

        String sql = """
                    INSERT INTO sale_item(
                      sale_id, line_number,
                      product_id, sku, description, presentation, factor,
                      quantity,
                      unit_price, discount_percent, discount_amount,
                      line_kind, gift_reason,
                      facturable_sunat, affects_stock, visible_in_document,
                      unit_cost_snapshot, total_cost_snapshot,
                      revenue_total
                    ) VALUES (
                      ?, ?,
                      ?, ?, ?, ?, ?,
                      ?,
                      ?, ?, ?,
                      ?, ?,
                      ?, ?, ?,
                      ?, ?,
                      ?
                    )
                    RETURNING id
                """;

        return jdbcClient.sql(sql)
                .params(
                        saleId, lineNumber,
                        productId, sku, description, presentation, factor,
                        quantity,
                        unitPrice, discountPercent, discountAmount,
                        lineKind, giftReason,
                        facturableSunat, affectsStock, visibleInDocument,
                        unitCostSnapshot, totalCostSnapshot,
                        revenueTotal
                )
                .query(Long.class)
                .single();
    }

    @Override
    public void updateTotals(Long saleId, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal igvAmount,
                             BigDecimal total, BigDecimal giftCostTotal) {

        String sql = """
                    UPDATE sale
                       SET subtotal = ?,
                           discount_total = ?,
                           igv_amount = ?,
                           total = ?,
                           gift_cost_total = ?,
                           updated_at = NOW()
                     WHERE id = ?
                """;

        jdbcClient.sql(sql)
                .params(subtotal, discountTotal, igvAmount, total, giftCostTotal, saleId)
                .update();
    }

    @Override
    public LockedSale lockById(Long saleId) {
        String sql = """
                    SELECT
                      id,
                      sale_session_id,
                      customer_id,
                      doc_type,
                      status,
                      payment_type,
                      total,
                      discount_total
                    FROM sale
                    WHERE id = ?
                    FOR UPDATE
                """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> LockedSale.builder()
                        .id(rs.getLong("id"))
                        .saleSessionId((Long) rs.getObject("sale_session_id"))
                        .customerId((Long) rs.getObject("customer_id"))
                        .docType(rs.getString("doc_type"))
                        .status(rs.getString("status"))
                        .paymentType(rs.getString("payment_type"))
                        .total(rs.getBigDecimal("total"))
                        .discountTotal(rs.getBigDecimal("discount_total"))
                        .build())
                .optional()
                .orElse(null);
    }

    @Override
    public List<SaleItemForVoid> findItemsBySaleId(Long saleId) {
        String sql = """
                    SELECT
                      id,
                      product_id,
                      quantity,
                      affects_stock,
                      unit_cost_snapshot,
                      total_cost_snapshot
                    FROM sale_item
                    WHERE sale_id = ?
                    ORDER BY line_number
                """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> SaleItemForVoid.builder()
                        .id(rs.getLong("id"))
                        .productId(rs.getLong("product_id"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .affectsStock(rs.getBoolean("affects_stock"))
                        .unitCostSnapshot(rs.getBigDecimal("unit_cost_snapshot"))
                        .totalCostSnapshot(rs.getBigDecimal("total_cost_snapshot"))
                        .build())
                .list();
    }

    @Override
    public void markAsVoided(Long saleId, String voidNote) {
        // No existe columna void_reason; se deja trazabilidad en notes.
        String sql = """
                    UPDATE sale
                       SET status = 'ANULADA',
                           notes = CASE
                                     WHEN ? IS NULL OR ? = '' THEN notes
                                     WHEN notes IS NULL OR notes = '' THEN ?
                                     ELSE notes || E'\n' || ?
                                   END,
                           updated_at = NOW()
                     WHERE id = ?
                """;

        jdbcClient.sql(sql)
                .params(voidNote, voidNote, voidNote, voidNote, saleId)
                .update();
    }
}

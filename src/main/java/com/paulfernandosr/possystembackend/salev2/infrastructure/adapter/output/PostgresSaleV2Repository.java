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
    public LockedEditableSale lockEditableById(Long saleId) {
        String sql = """
                    SELECT
                      id,
                      customer_id,
                      doc_type,
                      series,
                      number,
                      issue_date,
                      currency,
                      exchange_rate,
                      price_list,
                      customer_doc_type,
                      customer_doc_number,
                      customer_name,
                      customer_address,
                      tax_status,
                      tax_reason,
                      igv_rate,
                      igv_included,
                      payment_type,
                      credit_days,
                      due_date,
                      notes,
                      status,
                      sunat_status,
                      edit_status,
                      edit_count,
                      last_edited_at,
                      last_edited_by,
                      last_edit_reason,
                      total,
                      discount_total
                    FROM sale
                    WHERE id = ?
                    FOR UPDATE
                """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> LockedEditableSale.builder()
                        .id(rs.getLong("id"))
                        .customerId((Long) rs.getObject("customer_id"))
                        .docType(rs.getString("doc_type"))
                        .series(rs.getString("series"))
                        .number(rs.getLong("number"))
                        .issueDate(rs.getObject("issue_date", LocalDate.class))
                        .currency(rs.getString("currency"))
                        .exchangeRate(rs.getBigDecimal("exchange_rate"))
                        .priceList(rs.getString("price_list"))
                        .customerDocType(rs.getString("customer_doc_type"))
                        .customerDocNumber(rs.getString("customer_doc_number"))
                        .customerName(rs.getString("customer_name"))
                        .customerAddress(rs.getString("customer_address"))
                        .taxStatus(rs.getString("tax_status"))
                        .taxReason(rs.getString("tax_reason"))
                        .igvRate(rs.getBigDecimal("igv_rate"))
                        .igvIncluded((Boolean) rs.getObject("igv_included"))
                        .paymentType(rs.getString("payment_type"))
                        .creditDays((Integer) rs.getObject("credit_days"))
                        .dueDate(rs.getObject("due_date", LocalDate.class))
                        .notes(rs.getString("notes"))
                        .status(rs.getString("status"))
                        .sunatStatus(rs.getString("sunat_status"))
                        .editStatus(rs.getString("edit_status"))
                        .editCount((Integer) rs.getObject("edit_count"))
                        .lastEditedAt(rs.getTimestamp("last_edited_at") != null
                                ? rs.getTimestamp("last_edited_at").toLocalDateTime()
                                : null)
                        .lastEditedBy((Long) rs.getObject("last_edited_by"))
                        .lastEditReason(rs.getString("last_edit_reason"))
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
                      facturable_sunat,
                      affects_stock,
                      visible_in_document,
                      revenue_total,
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
                        .facturableSunat(rs.getObject("facturable_sunat", Boolean.class))
                        .affectsStock(rs.getObject("affects_stock", Boolean.class))
                        .visibleInDocument(rs.getObject("visible_in_document", Boolean.class))
                        .revenueTotal(rs.getBigDecimal("revenue_total"))
                        .unitCostSnapshot(rs.getBigDecimal("unit_cost_snapshot"))
                        .totalCostSnapshot(rs.getBigDecimal("total_cost_snapshot"))
                        .build())
                .list();
    }

    @Override
    public void deleteItemsBySaleId(Long saleId) {
        String sql = "DELETE FROM sale_item WHERE sale_id = ?";
        jdbcClient.sql(sql)
                .param(saleId)
                .update();
    }

    @Override
    public void updateHeaderForAdminEdit(Long saleId, LocalDate issueDate, String priceList, Long customerId,
                                         String customerDocType, String customerDocNumber, String customerName,
                                         String customerAddress, String taxStatus, String taxReason,
                                         BigDecimal igvRate, Boolean igvIncluded, Integer creditDays, LocalDate dueDate,
                                         BigDecimal subtotal, BigDecimal discountTotal, BigDecimal igvAmount,
                                         BigDecimal total, BigDecimal giftCostTotal, String notes,
                                         Long editedBy, String editReason) {
        String sql = """
                    UPDATE sale
                       SET issue_date = ?,
                           price_list = ?,
                           customer_id = ?,
                           customer_doc_type = ?,
                           customer_doc_number = ?,
                           customer_name = ?,
                           customer_address = ?,
                           tax_status = ?,
                           tax_reason = ?,
                           igv_rate = ?,
                           igv_included = ?,
                           credit_days = ?,
                           due_date = ?,
                           subtotal = ?,
                           discount_total = ?,
                           igv_amount = ?,
                           total = ?,
                           gift_cost_total = ?,
                           notes = ?,
                           edit_status = 'EDITADA',
                           edit_count = COALESCE(edit_count, 0) + 1,
                           last_edited_at = NOW(),
                           last_edited_by = ?,
                           last_edit_reason = ?,
                           sunat_status = CASE
                                            WHEN doc_type = 'SIMPLE' THEN 'NO_APLICA'
                                            ELSE 'NO_ENVIADO'
                                          END,
                           sunat_response_code = NULL,
                           sunat_response_description = NULL,
                           sunat_hash_code = NULL,
                           sunat_xml_path = NULL,
                           sunat_cdr_path = NULL,
                           sunat_pdf_path = NULL,
                           sunat_sent_at = NULL,
                           updated_at = NOW()
                     WHERE id = ?
                """;

        jdbcClient.sql(sql)
                .params(issueDate, priceList, customerId, customerDocType, customerDocNumber, customerName,
                        customerAddress, taxStatus, taxReason, igvRate, igvIncluded, creditDays, dueDate,
                        subtotal, discountTotal, igvAmount, total, giftCostTotal, notes,
                        editedBy, editReason, saleId)
                .update();
    }

    @Override
    public void insertEditHistory(Long saleId, String editReason, Long editedBy, String editedByUsername,
                                  String beforeSnapshotJson, String afterSnapshotJson) {
        String sql = """
                    INSERT INTO sale_edit_history(
                      sale_id,
                      edit_number,
                      edit_reason,
                      edited_by,
                      edited_by_username,
                      edited_at,
                      before_snapshot,
                      after_snapshot
                    )
                    SELECT
                      s.id,
                      s.edit_count,
                      ?,
                      ?,
                      ?,
                      NOW(),
                      CAST(? AS jsonb),
                      CAST(? AS jsonb)
                    FROM sale s
                    WHERE s.id = ?
                """;

        jdbcClient.sql(sql)
                .params(editReason, editedBy, editedByUsername, beforeSnapshotJson, afterSnapshotJson, saleId)
                .update();
    }

    @Override
    public void markAsVoided(Long saleId, String voidNote) {
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

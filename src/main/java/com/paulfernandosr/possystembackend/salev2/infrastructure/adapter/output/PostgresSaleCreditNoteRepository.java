package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleCreditNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresSaleCreditNoteRepository implements SaleCreditNoteRepository {

    private final JdbcClient jdbcClient;

    @Override
    public LockedSaleForCreditNote lockSale(Long saleId) {
        String sql = """
            SELECT
                id,
                customer_id,
                status,
                doc_type,
                series,
                number,
                issue_date,
                created_at,
                currency,
                customer_doc_type,
                customer_doc_number,
                customer_name,
                customer_address,
                tax_status,
                igv_rate,
                igv_included,
                subtotal,
                igv_amount,
                total,
                sunat_status
              FROM sale
             WHERE id = ?
             FOR UPDATE
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> LockedSaleForCreditNote.builder()
                        .saleId(rs.getLong("id"))
                        .customerId((Long) rs.getObject("customer_id"))
                        .status(rs.getString("status"))
                        .docType(rs.getString("doc_type"))
                        .series(rs.getString("series"))
                        .number(rs.getLong("number"))
                        .issueDate(rs.getObject("issue_date", java.time.LocalDate.class))
                        .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                        .currency(rs.getString("currency"))
                        .customerDocType(rs.getString("customer_doc_type"))
                        .customerDocNumber(rs.getString("customer_doc_number"))
                        .customerName(rs.getString("customer_name"))
                        .customerAddress(rs.getString("customer_address"))
                        .taxStatus(rs.getString("tax_status"))
                        .igvRate(rs.getBigDecimal("igv_rate"))
                        .igvIncluded(rs.getObject("igv_included", Boolean.class))
                        .subtotal(rs.getBigDecimal("subtotal"))
                        .igvAmount(rs.getBigDecimal("igv_amount"))
                        .total(rs.getBigDecimal("total"))
                        .sunatStatus(rs.getString("sunat_status"))
                        .build())
                .optional()
                .orElse(null);
    }

    @Override
    public List<SaleItemForCreditNote> findSaleItems(Long saleId) {
        String sql = """
            SELECT
                si.id AS sale_item_id,
                si.line_number,
                si.product_id,
                si.sku,
                si.description,
                si.presentation,
                si.factor,
                si.quantity,
                si.unit_price,
                si.discount_percent,
                si.discount_amount,
                si.line_kind,
                si.facturable_sunat,
                si.affects_stock,
                si.visible_in_document,
                si.unit_cost_snapshot,
                si.total_cost_snapshot,
                si.revenue_total,
                p.category AS product_category,
                counter_origin.counter_sale_item_id AS counter_sale_item_id,
                counter_origin.counter_sale_item_affects_stock AS counter_sale_item_affects_stock,
                (
                    SELECT psu.id
                      FROM product_serial_unit psu
                     WHERE psu.sale_item_id = si.id
                     ORDER BY psu.id
                     LIMIT 1
                ) AS serial_unit_id
              FROM sale_item si
              LEFT JOIN product p ON p.id = si.product_id
              LEFT JOIN LATERAL (
                    SELECT mapped.counter_sale_item_id,
                           csi.affects_stock AS counter_sale_item_affects_stock
                      FROM (
                            SELECT cscl.counter_sale_item_id,
                                   ROW_NUMBER() OVER (ORDER BY cscl.id) AS generated_line_number
                              FROM counter_sale_sunat_combo csc
                              JOIN counter_sale_sunat_combo_line cscl
                                ON cscl.combo_id = csc.id
                             WHERE csc.generated_sale_id = si.sale_id
                      ) mapped
                      JOIN counter_sale_item csi
                        ON csi.id = mapped.counter_sale_item_id
                     WHERE mapped.generated_line_number = si.line_number
                     LIMIT 1
              ) counter_origin ON TRUE
             WHERE si.sale_id = ?
             ORDER BY si.line_number
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> SaleItemForCreditNote.builder()
                        .saleItemId(rs.getLong("sale_item_id"))
                        .lineNumber(rs.getObject("line_number", Integer.class))
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
                        .facturableSunat(rs.getObject("facturable_sunat", Boolean.class))
                        .affectsStock(rs.getObject("affects_stock", Boolean.class))
                        .visibleInDocument(rs.getObject("visible_in_document", Boolean.class))
                        .unitCostSnapshot(rs.getBigDecimal("unit_cost_snapshot"))
                        .totalCostSnapshot(rs.getBigDecimal("total_cost_snapshot"))
                        .revenueTotal(rs.getBigDecimal("revenue_total"))
                        .productCategory(rs.getString("product_category"))
                        .serialUnitId((Long) rs.getObject("serial_unit_id"))
                        .counterSaleItemId((Long) rs.getObject("counter_sale_item_id"))
                        .counterSaleItemAffectsStock(rs.getObject("counter_sale_item_affects_stock", Boolean.class))
                        .build())
                .list();
    }

    @Override
    public Map<Long, BigDecimal> findCreditedQuantities(Long saleId) {
        String sql = """
            SELECT cni.sale_item_id,
                   COALESCE(SUM(cni.quantity), 0) AS credited_quantity
             FROM credit_note cn
             JOIN credit_note_item cni ON cni.credit_note_id = cn.id
             WHERE cn.sale_id = ?
               AND cn.status <> 'ANULADA'
             GROUP BY cni.sale_item_id
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> Map.entry(
                        rs.getLong("sale_item_id"),
                        rs.getBigDecimal("credited_quantity")
                ))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Long insertCreditNote(CreditNoteRecord record) {
        String sql = """
            INSERT INTO credit_note(
                sale_id,
                created_by,
                doc_type,
                series,
                number,
                issue_date,
                currency,
                customer_doc_type,
                customer_doc_number,
                customer_name,
                customer_address,
                tax_status,
                igv_rate,
                igv_included,
                credit_note_type_code,
                credit_note_type_description,
                reason,
                returned_to_stock,
                subtotal,
                igv_amount,
                total,
                status,
                sunat_status,
                created_at,
                updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                'EMITIDA',
                'PENDIENTE',
                NOW(),
                NOW()
            )
            RETURNING id
        """;

        return jdbcClient.sql(sql)
                .params(
                        record.getSaleId(),
                        record.getCreatedBy(),
                        record.getDocType(),
                        record.getSeries(),
                        record.getNumber(),
                        record.getIssueDate(),
                        record.getCurrency(),
                        record.getCustomerDocType(),
                        record.getCustomerDocNumber(),
                        record.getCustomerName(),
                        record.getCustomerAddress(),
                        record.getTaxStatus(),
                        record.getIgvRate(),
                        record.getIgvIncluded(),
                        record.getCreditNoteTypeCode(),
                        record.getCreditNoteTypeDescription(),
                        record.getReason(),
                        record.getReturnedToStock(),
                        record.getSubtotal(),
                        record.getIgvAmount(),
                        record.getTotal()
                )
                .query(Long.class)
                .single();
    }

    @Override
    public Long insertCreditNoteItem(CreditNoteItemRecord record) {
        String sql = """
            INSERT INTO credit_note_item(
                credit_note_id,
                sale_item_id,
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
                facturable_sunat,
                affects_stock,
                visible_in_document,
                unit_cost_snapshot,
                total_cost_snapshot,
                revenue_total,
                returned_to_stock,
                created_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()
            )
            RETURNING id
        """;

        return jdbcClient.sql(sql)
                .params(
                        record.getCreditNoteId(),
                        record.getSaleItemId(),
                        record.getLineNumber(),
                        record.getProductId(),
                        record.getSku(),
                        record.getDescription(),
                        record.getPresentation(),
                        record.getFactor(),
                        record.getQuantity(),
                        record.getUnitPrice(),
                        record.getDiscountPercent(),
                        record.getDiscountAmount(),
                        record.getFacturableSunat(),
                        record.getAffectsStock(),
                        record.getVisibleInDocument(),
                        record.getUnitCostSnapshot(),
                        record.getTotalCostSnapshot(),
                        record.getRevenueTotal(),
                        record.getReturnedToStock()
                )
                .query(Long.class)
                .single();
    }

    @Override
    public void updateEmissionResult(Long creditNoteId,
                                     String sunatStatus,
                                     String sunatCode,
                                     String sunatDescription,
                                     String hashCode,
                                     String xmlPath,
                                     String cdrPath,
                                     String pdfPath,
                                     LocalDateTime emittedAt) {
        String sql = """
            UPDATE credit_note
               SET sunat_status = ?,
                   sunat_response_code = ?,
                   sunat_response_description = ?,
                   sunat_hash_code = ?,
                   sunat_xml_path = ?,
                   sunat_cdr_path = ?,
                   sunat_pdf_path = ?,
                   sunat_sent_at = ?,
                   updated_at = NOW()
             WHERE id = ?
        """;

        jdbcClient.sql(sql)
                .params(sunatStatus, sunatCode, sunatDescription, hashCode, xmlPath, cdrPath, pdfPath, emittedAt, creditNoteId)
                .update();
    }

    @Override
    public List<CreditNoteView> findCreditNotesBySaleId(Long saleId) {
        String headerSql = """
            SELECT
                id,
                sale_id,
                doc_type,
                series,
                number,
                issue_date,
                credit_note_type_code,
                credit_note_type_description,
                reason,
                returned_to_stock,
                subtotal,
                igv_amount,
                total,
                status,
                sunat_status,
                sunat_response_code,
                sunat_response_description,
                sunat_hash_code,
                sunat_xml_path,
                sunat_cdr_path,
                sunat_pdf_path,
                sunat_sent_at
              FROM credit_note
             WHERE sale_id = ?
             ORDER BY created_at DESC, id DESC
        """;

        List<CreditNoteView> headers = jdbcClient.sql(headerSql)
                .param(saleId)
                .query((rs, rowNum) -> CreditNoteView.builder()
                        .creditNoteId(rs.getLong("id"))
                        .saleId(rs.getLong("sale_id"))
                        .docType(rs.getString("doc_type"))
                        .series(rs.getString("series"))
                        .number(rs.getLong("number"))
                        .issueDate(rs.getObject("issue_date", java.time.LocalDate.class))
                        .creditNoteTypeCode(rs.getString("credit_note_type_code"))
                        .creditNoteTypeDescription(rs.getString("credit_note_type_description"))
                        .reason(rs.getString("reason"))
                        .returnedToStock(rs.getObject("returned_to_stock", Boolean.class))
                        .subtotal(rs.getBigDecimal("subtotal"))
                        .igvAmount(rs.getBigDecimal("igv_amount"))
                        .total(rs.getBigDecimal("total"))
                        .status(rs.getString("status"))
                        .sunatStatus(rs.getString("sunat_status"))
                        .sunatCode(rs.getString("sunat_response_code"))
                        .sunatDescription(rs.getString("sunat_response_description"))
                        .hashCode(rs.getString("sunat_hash_code"))
                        .xmlPath(rs.getString("sunat_xml_path"))
                        .cdrPath(rs.getString("sunat_cdr_path"))
                        .pdfPath(rs.getString("sunat_pdf_path"))
                        .emittedAt(rs.getTimestamp("sunat_sent_at") != null ? rs.getTimestamp("sunat_sent_at").toLocalDateTime() : null)
                        .items(new java.util.ArrayList<>())
                        .build())
                .list();

        if (headers.isEmpty()) {
            return headers;
        }

        String itemsSql = """
            SELECT
                cni.id,
                cni.credit_note_id,
                cni.sale_item_id,
                cni.product_id,
                cni.sku,
                cni.description,
                cni.quantity,
                cni.revenue_total,
                cni.returned_to_stock
              FROM credit_note_item cni
              JOIN credit_note cn ON cn.id = cni.credit_note_id
             WHERE cn.sale_id = ?
             ORDER BY cni.credit_note_id, cni.line_number
        """;

        List<CreditNoteItemView> items = jdbcClient.sql(itemsSql)
                .param(saleId)
                .query((rs, rowNum) -> CreditNoteItemView.builder()
                        .creditNoteItemId(rs.getLong("id"))
                        .creditNoteId(rs.getLong("credit_note_id"))
                        .saleItemId(rs.getLong("sale_item_id"))
                        .productId(rs.getLong("product_id"))
                        .sku(rs.getString("sku"))
                        .description(rs.getString("description"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .revenueTotal(rs.getBigDecimal("revenue_total"))
                        .returnedToStock(rs.getObject("returned_to_stock", Boolean.class))
                        .build())
                .list();

        Map<Long, List<CreditNoteItemView>> itemsByCreditNote = items.stream()
                .collect(Collectors.groupingBy(CreditNoteItemView::getCreditNoteId));

        for (CreditNoteView header : headers) {
            header.setItems(itemsByCreditNote.getOrDefault(header.getCreditNoteId(), List.of()));
        }

        return headers;
    }
}

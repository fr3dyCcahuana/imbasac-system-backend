package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2SunatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresSaleV2SunatRepository implements SaleV2SunatRepository {

    private final JdbcClient jdbcClient;

    @Override
    public LockedSunatSale lockSale(Long saleId) {
        String sql = """
            SELECT
                s.id                AS sale_id,
                s.status            AS status,
                s.doc_type          AS doc_type,
                s.series            AS series,
                s.number            AS number,
                s.issue_date        AS issue_date,
                s.created_at        AS created_at,
                s.currency          AS currency,
                s.customer_doc_type   AS customer_doc_type,
                s.customer_doc_number AS customer_doc_number,
                s.customer_name       AS customer_name,
                s.customer_address    AS customer_address,
                COALESCE(s.customer_ubigeo, c_id.ubigeo, c_doc.ubigeo) AS customer_ubigeo,
                COALESCE(s.customer_department, c_id.department, c_doc.department) AS customer_department,
                COALESCE(s.customer_province, c_id.province, c_doc.province) AS customer_province,
                COALESCE(s.customer_district, c_id.district, c_doc.district) AS customer_district,
                s.tax_status        AS tax_status,
                s.subtotal          AS subtotal,
                s.discount_total    AS discount_total,
                s.igv_amount        AS igv_amount,
                s.total             AS total,
                s.payment_type      AS payment_type,
                s.notes             AS notes,
                s.sunat_status      AS sunat_status,
                s.sunat_response_code        AS sunat_response_code,
                s.sunat_response_description AS sunat_response_description,
                s.sunat_hash_code           AS sunat_hash_code,
                s.sunat_xml_path            AS sunat_xml_path,
                s.sunat_cdr_path            AS sunat_cdr_path,
                s.sunat_pdf_path            AS sunat_pdf_path,
                s.sunat_sent_at             AS sunat_sent_at
            FROM sale s
            LEFT JOIN customers c_id
                   ON c_id.id = s.customer_id
            LEFT JOIN customers c_doc
                   ON s.customer_id IS NULL
                  AND c_doc.document_type = s.customer_doc_type
                  AND c_doc.document_number = s.customer_doc_number
            WHERE s.id = ?
            FOR UPDATE OF s
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> LockedSunatSale.builder()
                        .saleId(rs.getLong("sale_id"))
                        .status(rs.getString("status"))
                        .docType(rs.getString("doc_type"))
                        .series(rs.getString("series"))
                        .number(rs.getLong("number"))
                        .issueDate(rs.getDate("issue_date") != null ? rs.getDate("issue_date").toLocalDate() : null)
                        .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                        .currency(rs.getString("currency"))
                        .customerDocType(rs.getString("customer_doc_type"))
                        .customerDocNumber(rs.getString("customer_doc_number"))
                        .customerName(rs.getString("customer_name"))
                        .customerAddress(rs.getString("customer_address"))
                        .customerUbigeo(rs.getString("customer_ubigeo"))
                        .customerDepartment(rs.getString("customer_department"))
                        .customerProvince(rs.getString("customer_province"))
                        .customerDistrict(rs.getString("customer_district"))
                        .taxStatus(rs.getString("tax_status"))
                        .subtotal(rs.getBigDecimal("subtotal"))
                        .discountTotal(rs.getBigDecimal("discount_total"))
                        .igvAmount(rs.getBigDecimal("igv_amount"))
                        .total(rs.getBigDecimal("total"))
                        .paymentType(rs.getString("payment_type"))
                        .notes(rs.getString("notes"))
                        .sunatStatus(rs.getString("sunat_status"))
                        .sunatResponseCode(rs.getString("sunat_response_code"))
                        .sunatResponseDescription(rs.getString("sunat_response_description"))
                        .sunatHashCode(rs.getString("sunat_hash_code"))
                        .sunatXmlPath(rs.getString("sunat_xml_path"))
                        .sunatCdrPath(rs.getString("sunat_cdr_path"))
                        .sunatPdfPath(rs.getString("sunat_pdf_path"))
                        .sunatSentAt(rs.getTimestamp("sunat_sent_at") != null ? rs.getTimestamp("sunat_sent_at").toLocalDateTime() : null)
                        .build())
                .optional()
                .orElse(null);
    }

    @Override
    public List<SaleItemForSunat> findItems(Long saleId) {
        String sql = """
            SELECT
                si.line_number,
                si.product_id,
                si.sku,
                si.description,
                p.category AS product_category,
                si.quantity,
                si.revenue_total,
                si.line_kind,
                si.visible_in_document
            FROM sale_item si
            LEFT JOIN product p ON p.id = si.product_id
            WHERE si.sale_id = ?
            ORDER BY si.line_number
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
                .query((rs, rowNum) -> SaleItemForSunat.builder()
                        .lineNumber(rs.getInt("line_number"))
                        .productId(rs.getObject("product_id") != null ? rs.getLong("product_id") : null)
                        .sku(rs.getString("sku"))
                        .description(rs.getString("description"))
                        .productCategory(rs.getString("product_category"))
                        .quantity(rs.getBigDecimal("quantity"))
                        .revenueTotal(rs.getBigDecimal("revenue_total"))
                        .lineKind(rs.getString("line_kind"))
                        .visibleInDocument(rs.getBoolean("visible_in_document"))
                        .build())
                .list();
    }

    @Override
    public void updateEmissionResult(Long saleId, String sunatStatus, String sunatCode, String sunatDescription,
                                     String hashCode, String xmlPath, String cdrPath, String pdfPath,
                                     LocalDateTime emittedAt) {
        String sql = """
            UPDATE sale
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
                .params(sunatStatus, sunatCode, sunatDescription, hashCode, xmlPath, cdrPath, pdfPath, emittedAt, saleId)
                .update();
    }

    @Override
    public void markEmissionError(Long saleId, String description, LocalDateTime emittedAt) {
        updateEmissionResult(saleId, "ERROR", null, description, null, null, null, null, emittedAt);
    }
}

package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2QueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostgresSaleV2QueryRepository implements SaleV2QueryRepository {

    private final JdbcClient jdbcClient;

    private void appendCommonSalesFilters(StringBuilder sql,
                                          List<Object> params,
                                          String likeParam,
                                          String docType,
                                          String series,
                                          Long number,
                                          String status,
                                          String sunatStatus,
                                          String editStatus,
                                          String paymentType,
                                          LocalDate dateFrom,
                                          LocalDate dateTo) {
        if (docType != null && !docType.isBlank()) {
            sql.append(" AND s.doc_type = ? ");
            params.add(docType);
        }

        if (series != null && !series.isBlank()) {
            sql.append(" AND UPPER(TRIM(s.series)) = ? ");
            params.add(series);
        }

        if (number != null) {
            sql.append(" AND s.number = ? ");
            params.add(number);
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ? ");
            params.add(status);
        }

        if (sunatStatus != null && !sunatStatus.isBlank()) {
            sql.append(" AND s.sunat_status = ? ");
            params.add(sunatStatus);
        }

        if (editStatus != null && !editStatus.isBlank()) {
            sql.append(" AND COALESCE(s.edit_status, 'NO_EDITADA') = ? ");
            params.add(editStatus);
        }

        if (paymentType != null && !paymentType.isBlank()) {
            sql.append(" AND s.payment_type = ? ");
            params.add(paymentType);
        }

        if (dateFrom != null) {
            sql.append(" AND s.issue_date >= ? ");
            params.add(dateFrom);
        }

        if (dateTo != null) {
            sql.append(" AND s.issue_date <= ? ");
            params.add(dateTo);
        }

        sql.append("""
        AND (
            ? = '%'
            OR COALESCE(s.customer_name, '') ILIKE ?
            OR COALESCE(s.customer_doc_number, '') ILIKE ?
            OR (s.doc_type || ' ' || s.series || '-' || CAST(s.number AS TEXT)) ILIKE ?
            OR (s.series || '-' || CAST(s.number AS TEXT)) ILIKE ?
        )
    """);

        params.add(likeParam);
        params.add(likeParam);
        params.add(likeParam);
        params.add(likeParam);
        params.add(likeParam);
    }

    private boolean isEditableForList(String saleStatus, String sunatStatus) {
        String normalizedSaleStatus = saleStatus == null ? "" : saleStatus.trim().toUpperCase();
        String normalizedSunatStatus = sunatStatus == null ? "" : sunatStatus.trim().toUpperCase();

        return "EMITIDA".equals(normalizedSaleStatus)
                && ("NO_ENVIADO".equals(normalizedSunatStatus)
                || "ERROR".equals(normalizedSunatStatus)
                || "RECHAZADO".equals(normalizedSunatStatus)
                || "NO_APLICA".equals(normalizedSunatStatus));
    }

    private boolean isEmittableForList(String saleStatus, String docType, String sunatStatus) {
        String normalizedSaleStatus = saleStatus == null ? "" : saleStatus.trim().toUpperCase();
        String normalizedDocType = docType == null ? "" : docType.trim().toUpperCase();
        String normalizedSunatStatus = sunatStatus == null ? "" : sunatStatus.trim().toUpperCase();

        return "EMITIDA".equals(normalizedSaleStatus)
                && ("BOLETA".equals(normalizedDocType) || "FACTURA".equals(normalizedDocType))
                && ("NO_ENVIADO".equals(normalizedSunatStatus)
                || "ERROR".equals(normalizedSunatStatus)
                || "ERROR_COMUNICACION".equals(normalizedSunatStatus)
                || "RECHAZADO".equals(normalizedSunatStatus));
    }

    private String buildFullName(String firstName, String lastName) {
        String fullName = ((firstName == null ? "" : firstName.trim()) + " "
                + (lastName == null ? "" : lastName.trim())).trim();
        return fullName.isBlank() ? null : fullName;
    }

    private SaleV2RelationInfoResponse buildRelationInfo(java.sql.ResultSet rs) throws java.sql.SQLException {
        Long proformaId = getLong(rs, "proforma_id");
        String proformaSeries = rs.getString("proforma_series");
        Long proformaNumber = getLong(rs, "proforma_number");

        Long contractId = getLong(rs, "contract_id");
        String contractSeries = rs.getString("contract_series");
        Long contractNumber = getLong(rs, "contract_number");

        Long directComboId = getLong(rs, "direct_combo_id");
        String directComboStatus = rs.getString("direct_combo_status");
        Integer directComboCount = getInteger(rs, "direct_combo_counter_sale_count");
        String directCounterSaleDocs = rs.getString("direct_counter_sale_documents_label");

        Integer compositionCount = getInteger(rs, "composition_counter_sale_count");
        Integer compositionPendingCount = getInteger(rs, "composition_pending_counter_sale_count");
        Integer compositionAcceptedCount = getInteger(rs, "composition_accepted_counter_sale_count");
        String compositionCounterSaleDocs = rs.getString("composition_counter_sale_documents_label");

        if (contractId != null) {
            String contractDoc = formatDoc(contractSeries, contractNumber);
            String label = contractDoc != null ? "Contrato " + contractDoc : "Contrato #" + contractId;

            return SaleV2RelationInfoResponse.builder()
                    .relationType("CONTRACT")
                    .relationLabel(label)
                    .proformaId(null)
                    .proformaSeries(null)
                    .proformaNumber(null)
                    .contractId(contractId)
                    .contractSeries(contractSeries)
                    .contractNumber(contractNumber)
                    .counterSaleComboId(null)
                    .counterSaleComboStatus(null)
                    .counterSaleDocumentsLabel(null)
                    .counterSaleCount(0)
                    .pendingCounterSaleCount(0)
                    .acceptedCounterSaleCount(0)
                    .hasCounterSaleRelation(false)
                    .hasPendingCounterSaleRelation(false)
                    .build();
        }

        if (directComboId != null) {
            int count = directComboCount == null ? 0 : directComboCount;
            boolean pending = "PENDING".equalsIgnoreCase(directComboStatus)
                    || "ERROR_COMUNICACION".equalsIgnoreCase(directComboStatus)
                    || "ERROR".equalsIgnoreCase(directComboStatus)
                    || "RECHAZADO".equalsIgnoreCase(directComboStatus);

            String label = hasText(directCounterSaleDocs)
                    ? "Ventanilla " + directCounterSaleDocs
                    : "Venta diaria de ventanilla" + (count > 0 ? " (" + count + ")" : "");

            return SaleV2RelationInfoResponse.builder()
                    .relationType("COUNTER_SALE_DAILY")
                    .relationLabel(label)
                    .proformaId(null)
                    .proformaSeries(null)
                    .proformaNumber(null)
                    .contractId(null)
                    .contractSeries(null)
                    .contractNumber(null)
                    .counterSaleComboId(directComboId)
                    .counterSaleComboStatus(directComboStatus)
                    .counterSaleDocumentsLabel(directCounterSaleDocs)
                    .counterSaleCount(count)
                    .pendingCounterSaleCount(pending ? count : 0)
                    .acceptedCounterSaleCount("ACEPTADO".equalsIgnoreCase(directComboStatus) ? count : 0)
                    .hasCounterSaleRelation(count > 0)
                    .hasPendingCounterSaleRelation(pending)
                    .build();
        }

        int compCount = compositionCount == null ? 0 : compositionCount;
        if (compCount > 0) {
            int pendingCount = compositionPendingCount == null ? 0 : compositionPendingCount;
            int acceptedCount = compositionAcceptedCount == null ? 0 : compositionAcceptedCount;

            String label = hasText(compositionCounterSaleDocs)
                    ? "SUNAT + ventanilla " + compositionCounterSaleDocs
                    : "SUNAT + ventanilla" + (compCount > 0 ? " (" + compCount + ")" : "");

            return SaleV2RelationInfoResponse.builder()
                    .relationType("COUNTER_SALE_COMPOSITION")
                    .relationLabel(label)
                    .proformaId(null)
                    .proformaSeries(null)
                    .proformaNumber(null)
                    .contractId(null)
                    .contractSeries(null)
                    .contractNumber(null)
                    .counterSaleComboId(null)
                    .counterSaleComboStatus(null)
                    .counterSaleDocumentsLabel(compositionCounterSaleDocs)
                    .counterSaleCount(compCount)
                    .pendingCounterSaleCount(pendingCount)
                    .acceptedCounterSaleCount(acceptedCount)
                    .hasCounterSaleRelation(true)
                    .hasPendingCounterSaleRelation(pendingCount > 0)
                    .build();
        }

        if (proformaId != null) {
            String proformaDoc = formatDoc(proformaSeries, proformaNumber);
            String label = proformaDoc != null ? "Proforma " + proformaDoc : "Proforma #" + proformaId;

            return SaleV2RelationInfoResponse.builder()
                    .relationType("PROFORMA")
                    .relationLabel(label)
                    .proformaId(proformaId)
                    .proformaSeries(proformaSeries)
                    .proformaNumber(proformaNumber)
                    .contractId(null)
                    .contractSeries(null)
                    .contractNumber(null)
                    .counterSaleComboId(null)
                    .counterSaleComboStatus(null)
                    .counterSaleDocumentsLabel(null)
                    .counterSaleCount(0)
                    .pendingCounterSaleCount(0)
                    .acceptedCounterSaleCount(0)
                    .hasCounterSaleRelation(false)
                    .hasPendingCounterSaleRelation(false)
                    .build();
        }

        return SaleV2RelationInfoResponse.builder()
                .relationType("NORMAL")
                .relationLabel("Venta directa")
                .proformaId(null)
                .proformaSeries(null)
                .proformaNumber(null)
                .contractId(null)
                .contractSeries(null)
                .contractNumber(null)
                .counterSaleComboId(null)
                .counterSaleComboStatus(null)
                .counterSaleDocumentsLabel(null)
                .counterSaleCount(0)
                .pendingCounterSaleCount(0)
                .acceptedCounterSaleCount(0)
                .hasCounterSaleRelation(false)
                .hasPendingCounterSaleRelation(false)
                .build();
    }

    private String formatDoc(String series, Long number) {
        if (series == null || series.isBlank() || number == null) {
            return null;
        }

        return series.trim() + "-" + String.format("%08d", number);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Long getLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer getInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        return value instanceof Number number ? number.intValue() : null;
    }

    @Override
    public long countSales(String likeParam,
                           String docType,
                           String series,
                           Long number,
                           String status,
                           String sunatStatus,
                           String editStatus,
                           String paymentType,
                           LocalDate dateFrom,
                           LocalDate dateTo) {
        StringBuilder sql = new StringBuilder("""
        SELECT COUNT(1)
          FROM sale s
         WHERE 1 = 1
    """);

        List<Object> params = new ArrayList<>();
        appendCommonSalesFilters(sql, params, likeParam, docType, series, number, status, sunatStatus, editStatus, paymentType, dateFrom, dateTo);

        return jdbcClient.sql(sql.toString())
                .params(params)
                .query(Long.class)
                .single();
    }

    @Override
    public List<SaleV2SummaryResponse> findSalesPage(String likeParam,
                                                     String docType,
                                                     String series,
                                                     Long number,
                                                     String status,
                                                     String sunatStatus,
                                                     String editStatus,
                                                     String paymentType,
                                                     LocalDate dateFrom,
                                                     LocalDate dateTo,
                                                     int limit,
                                                     int offset) {
        StringBuilder sql = new StringBuilder("""
        SELECT
            s.id          AS sale_id,
            s.doc_type    AS doc_type,
            s.series      AS series,
            s.number      AS number,
            s.issue_date  AS issue_date,
            s.customer_doc_number AS customer_doc_number,
            s.customer_name       AS customer_name,
            s.payment_type AS payment_type,
            s.total       AS total,
            s.status      AS status,
            s.sunat_status AS sunat_status,
            s.sunat_response_code AS sunat_response_code,
            s.sunat_response_description AS sunat_response_description,
            s.sunat_sent_at AS sunat_sent_at,
            COALESCE(s.edit_status, 'NO_EDITADA') AS edit_status,
            COALESCE(s.edit_count, 0) AS edit_count,
            s.last_edited_at AS last_edited_at,
            u_edit.username AS last_edited_by_username,
            s.created_at AS created_at,
            s.updated_at AS updated_at,

            COALESCE(sr.proforma_id, s.source_proforma_id) AS proforma_id,
            COALESCE(pf.series, p_ref.series) AS proforma_series,
            COALESCE(pf.number, p_ref.number) AS proforma_number,

            s.contract_id AS contract_id,
            ct.series AS contract_series,
            ct.number AS contract_number,

            direct_combo.combo_id AS direct_combo_id,
            direct_combo.combo_status AS direct_combo_status,
            direct_combo.counter_sale_documents_label AS direct_counter_sale_documents_label,
            COALESCE(direct_combo.counter_sale_count, 0) AS direct_combo_counter_sale_count,

            comp.counter_sale_documents_label AS composition_counter_sale_documents_label,
            COALESCE(comp.counter_sale_count, 0) AS composition_counter_sale_count,
            COALESCE(comp.pending_counter_sale_count, 0) AS composition_pending_counter_sale_count,
            COALESCE(comp.accepted_counter_sale_count, 0) AS composition_accepted_counter_sale_count
          FROM sale s
          LEFT JOIN users u_edit
                 ON u_edit.id = s.last_edited_by
          LEFT JOIN sale_reference sr
                 ON sr.sale_id = s.id
          LEFT JOIN proforma pf
                 ON pf.id = sr.proforma_id
          LEFT JOIN proforma p_ref
                 ON p_ref.id = s.source_proforma_id
          LEFT JOIN contract ct
                 ON ct.id = s.contract_id
          LEFT JOIN LATERAL (
                SELECT csc.id AS combo_id,
                       csc.combo_status AS combo_status,
                       COUNT(csm.counter_sale_id)::int AS counter_sale_count,
                       STRING_AGG(
                           cs.series || '-' || LPAD(cs.number::text, 8, '0'),
                           ', '
                           ORDER BY csm.position
                       ) AS counter_sale_documents_label
                  FROM counter_sale_sunat_combo csc
                  LEFT JOIN counter_sale_sunat_combo_member csm
                         ON csm.combo_id = csc.id
                  LEFT JOIN counter_sale cs
                         ON cs.id = csm.counter_sale_id
                 WHERE csc.generated_sale_id = s.id
                 GROUP BY csc.id, csc.combo_status
                 ORDER BY csc.id DESC
                 LIMIT 1
          ) direct_combo ON TRUE
          LEFT JOIN LATERAL (
                SELECT COUNT(*)::int AS counter_sale_count,
                       COUNT(*) FILTER (
                           WHERE l.reservation_status IN ('PENDING', 'ERROR_COMUNICACION')
                       )::int AS pending_counter_sale_count,
                       COUNT(*) FILTER (
                           WHERE l.reservation_status = 'ACEPTADO'
                       )::int AS accepted_counter_sale_count,
                       STRING_AGG(
                           cs.series || '-' || LPAD(cs.number::text, 8, '0'),
                           ', '
                           ORDER BY cs.series, cs.number
                       ) AS counter_sale_documents_label
                  FROM sale_counter_sale_sunat_link l
                  JOIN counter_sale cs
                    ON cs.id = l.counter_sale_id
                 WHERE l.sale_id = s.id
                   AND l.reservation_status <> 'LIBERADO'
          ) comp ON TRUE
         WHERE 1 = 1
    """);

        List<Object> params = new ArrayList<>();
        appendCommonSalesFilters(sql, params, likeParam, docType, series, number, status, sunatStatus, editStatus, paymentType, dateFrom, dateTo);

        sql.append("""
        ORDER BY s.issue_date DESC, s.id DESC
        LIMIT ?
        OFFSET ?
    """);

        params.add(limit);
        params.add(offset);

        RowMapper<SaleV2SummaryResponse> mapper = (rs, rowNum) -> {
            String saleStatusValue = rs.getString("status");
            String docTypeValue = rs.getString("doc_type");
            String sunatStatusValue = rs.getString("sunat_status");

            return SaleV2SummaryResponse.builder()
                    .saleId(rs.getLong("sale_id"))
                    .docType(docTypeValue)
                    .series(rs.getString("series"))
                    .number(rs.getLong("number"))
                    .issueDate(rs.getDate("issue_date").toLocalDate())
                    .customerDocNumber(rs.getString("customer_doc_number"))
                    .customerName(rs.getString("customer_name"))
                    .paymentType(rs.getString("payment_type"))
                    .total(rs.getBigDecimal("total"))
                    .status(saleStatusValue)
                    .sunatStatus(sunatStatusValue)
                    .sunatResponseCode(rs.getString("sunat_response_code"))
                    .sunatResponseDescription(rs.getString("sunat_response_description"))
                    .sunatSentAt(rs.getTimestamp("sunat_sent_at") != null ? rs.getTimestamp("sunat_sent_at").toLocalDateTime() : null)
                    .editStatus(rs.getString("edit_status"))
                    .editCount(getInteger(rs, "edit_count"))
                    .lastEditedAt(rs.getTimestamp("last_edited_at") != null ? rs.getTimestamp("last_edited_at").toLocalDateTime() : null)
                    .lastEditedByUsername(rs.getString("last_edited_by_username"))
                    .canEditBeforeSunat(isEditableForList(saleStatusValue, sunatStatusValue))
                    .canEmitSunat(isEmittableForList(saleStatusValue, docTypeValue, sunatStatusValue))
                    .contractId(getLong(rs, "contract_id"))
                    .relation(buildRelationInfo(rs))
                    .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                    .build();
        };

        return jdbcClient.sql(sql.toString())
                .params(params)
                .query(mapper)
                .list();
    }

    @Override
    public List<SaleV2SummaryItemResponse> findSaleSummaryItemsBySaleIds(List<Long> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) {
            return List.of();
        }

        String placeholders = saleIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = String.format("""
        SELECT
            si.sale_id AS sale_id,
            si.quantity AS quantity,
            si.sku AS sku,
            si.description AS description,
            si.presentation AS presentation,
            si.line_number AS line_number
          FROM sale_item si
         WHERE si.sale_id IN (%s)
         ORDER BY si.sale_id ASC, si.line_number ASC
    """, placeholders);

        RowMapper<SaleV2SummaryItemResponse> mapper = (rs, rowNum) -> SaleV2SummaryItemResponse.builder()
                .saleId(rs.getLong("sale_id"))
                .quantity(rs.getBigDecimal("quantity"))
                .sku(rs.getString("sku"))
                .description(rs.getString("description"))
                .presentation(rs.getString("presentation"))
                .build();

        return jdbcClient.sql(sql)
                .params(saleIds.toArray())
                .query(mapper)
                .list();
    }

    @Override
    public SaleV2DetailResponse findSaleDetail(Long saleId) {
        String sql = """
            SELECT
                s.id              AS sale_id,
                s.station_id      AS station_id,
                s.sale_session_id AS sale_session_id,
                s.created_by      AS created_by,
                u_created.id      AS created_by_user_id,
                u_created.username AS created_by_username,
                u_created.first_name AS created_by_first_name,
                u_created.last_name  AS created_by_last_name,
                s.doc_type        AS doc_type,
                s.series          AS series,
                s.number          AS number,
                s.issue_date      AS issue_date,
                s.currency        AS currency,
                s.exchange_rate   AS exchange_rate,
                s.price_list      AS price_list,
                s.customer_id     AS customer_id,
                s.customer_doc_type   AS customer_doc_type,
                s.customer_doc_number AS customer_doc_number,
                s.customer_name       AS customer_name,
                s.customer_address    AS customer_address,
                COALESCE(s.customer_ubigeo, c_id.ubigeo, c_doc.ubigeo) AS customer_ubigeo,
                COALESCE(s.customer_department, c_id.department, c_doc.department) AS customer_department,
                COALESCE(s.customer_province, c_id.province, c_doc.province) AS customer_province,
                COALESCE(s.customer_district, c_id.district, c_doc.district) AS customer_district,
                s.tax_status      AS tax_status,
                s.tax_reason      AS tax_reason,
                s.igv_rate        AS igv_rate,
                s.igv_included    AS igv_included,
                s.payment_type    AS payment_type,
                s.credit_days     AS credit_days,
                s.due_date        AS due_date,
                s.subtotal        AS subtotal,
                s.discount_total  AS discount_total,
                s.igv_amount      AS igv_amount,
                s.total           AS total,
                s.gift_cost_total AS gift_cost_total,
                s.notes           AS notes,
                s.status          AS status,
                s.contract_id     AS contract_id,
                s.created_at      AS created_at,
                s.updated_at      AS updated_at,

                COALESCE(sr.proforma_id, s.source_proforma_id) AS proforma_id,
                COALESCE(pf.series, p_ref.series) AS proforma_series,
                COALESCE(pf.number, p_ref.number) AS proforma_number,

                ct.series AS contract_series,
                ct.number AS contract_number,

                direct_combo.combo_id AS direct_combo_id,
                direct_combo.combo_status AS direct_combo_status,
                direct_combo.counter_sale_documents_label AS direct_counter_sale_documents_label,
                COALESCE(direct_combo.counter_sale_count, 0) AS direct_combo_counter_sale_count,

                comp.counter_sale_documents_label AS composition_counter_sale_documents_label,
                COALESCE(comp.counter_sale_count, 0) AS composition_counter_sale_count,
                COALESCE(comp.pending_counter_sale_count, 0) AS composition_pending_counter_sale_count,
                COALESCE(comp.accepted_counter_sale_count, 0) AS composition_accepted_counter_sale_count,

                COALESCE(sr.proforma_id, s.source_proforma_id) AS reference_proforma_id,
                COALESCE(sr.imported_at, p_ref.converted_at) AS reference_imported_at,

                s.sunat_status               AS sunat_status,
                s.sunat_response_code        AS sunat_response_code,
                s.sunat_response_description AS sunat_response_description,
                s.sunat_hash_code            AS sunat_hash_code,
                s.sunat_xml_path             AS sunat_xml_path,
                s.sunat_cdr_path             AS sunat_cdr_path,
                s.sunat_pdf_path             AS sunat_pdf_path,
                s.sunat_sent_at              AS sunat_sent_at,

                s.edit_status                AS edit_status,
                s.edit_count                 AS edit_count,
                s.last_edited_at             AS last_edited_at,
                s.last_edited_by             AS last_edited_by,
                s.last_edit_reason           AS last_edit_reason,
                u_edit.username              AS last_edited_by_username

              FROM sale s
              LEFT JOIN sale_reference sr
                     ON sr.sale_id = s.id
              LEFT JOIN proforma pf
                     ON pf.id = sr.proforma_id
              LEFT JOIN proforma p_ref
                     ON p_ref.id = s.source_proforma_id
              LEFT JOIN users u_created
                     ON u_created.id = s.created_by
              LEFT JOIN users u_edit
                     ON u_edit.id = s.last_edited_by
              LEFT JOIN customers c_id
                     ON c_id.id = s.customer_id
              LEFT JOIN customers c_doc
                     ON s.customer_id IS NULL
                    AND c_doc.document_type = s.customer_doc_type
                    AND c_doc.document_number = s.customer_doc_number
              LEFT JOIN contract ct
                     ON ct.id = s.contract_id
              LEFT JOIN LATERAL (
                    SELECT csc.id AS combo_id,
                           csc.combo_status AS combo_status,
                           COUNT(csm.counter_sale_id)::int AS counter_sale_count,
                           STRING_AGG(
                               cs.series || '-' || LPAD(cs.number::text, 8, '0'),
                               ', '
                               ORDER BY csm.position
                           ) AS counter_sale_documents_label
                      FROM counter_sale_sunat_combo csc
                      LEFT JOIN counter_sale_sunat_combo_member csm
                             ON csm.combo_id = csc.id
                      LEFT JOIN counter_sale cs
                             ON cs.id = csm.counter_sale_id
                     WHERE csc.generated_sale_id = s.id
                     GROUP BY csc.id, csc.combo_status
                     ORDER BY csc.id DESC
                     LIMIT 1
              ) direct_combo ON TRUE
              LEFT JOIN LATERAL (
                    SELECT COUNT(*)::int AS counter_sale_count,
                           COUNT(*) FILTER (
                               WHERE l.reservation_status IN ('PENDING', 'ERROR_COMUNICACION')
                           )::int AS pending_counter_sale_count,
                           COUNT(*) FILTER (
                               WHERE l.reservation_status = 'ACEPTADO'
                           )::int AS accepted_counter_sale_count,
                           STRING_AGG(
                               cs.series || '-' || LPAD(cs.number::text, 8, '0'),
                               ', '
                               ORDER BY cs.series, cs.number
                           ) AS counter_sale_documents_label
                      FROM sale_counter_sale_sunat_link l
                      JOIN counter_sale cs
                        ON cs.id = l.counter_sale_id
                     WHERE l.sale_id = s.id
                       AND l.reservation_status <> 'LIBERADO'
              ) comp ON TRUE
             WHERE s.id = ?
        """;

        RowMapper<SaleV2DetailResponse> mapper = (rs, rowNum) -> {
            SaleV2UserInfoResponse createdByUser = null;
            Object createdByUserId = rs.getObject("created_by_user_id");
            if (createdByUserId != null) {
                String firstName = rs.getString("created_by_first_name");
                String lastName = rs.getString("created_by_last_name");

                createdByUser = SaleV2UserInfoResponse.builder()
                        .id(((Number) createdByUserId).longValue())
                        .username(rs.getString("created_by_username"))
                        .firstName(firstName)
                        .lastName(lastName)
                        .fullName(buildFullName(firstName, lastName))
                        .build();
            }

            SaleV2ReferenceResponse reference = null;
            Object refProformaId = rs.getObject("reference_proforma_id");
            if (refProformaId != null) {
                reference = SaleV2ReferenceResponse.builder()
                        .proformaId(((Number) refProformaId).longValue())
                        .importedAt(rs.getTimestamp("reference_imported_at") != null
                                ? rs.getTimestamp("reference_imported_at").toLocalDateTime()
                                : null)
                        .build();
            }

            SaleV2SunatInfoResponse sunat = SaleV2SunatInfoResponse.builder()
                    .status(rs.getString("sunat_status"))
                    .responseCode(rs.getString("sunat_response_code"))
                    .responseDescription(rs.getString("sunat_response_description"))
                    .hashCode(rs.getString("sunat_hash_code"))
                    .xmlPath(rs.getString("sunat_xml_path"))
                    .cdrPath(rs.getString("sunat_cdr_path"))
                    .pdfPath(rs.getString("sunat_pdf_path"))
                    .sentAt(rs.getTimestamp("sunat_sent_at") != null
                            ? rs.getTimestamp("sunat_sent_at").toLocalDateTime()
                            : null)
                    .accepted("ACEPTADO".equalsIgnoreCase(rs.getString("sunat_status")))
                    .rejected("RECHAZADO".equalsIgnoreCase(rs.getString("sunat_status")))
                    .communicationError("ERROR_COMUNICACION".equalsIgnoreCase(rs.getString("sunat_status")))
                    .retryable("ERROR_COMUNICACION".equalsIgnoreCase(rs.getString("sunat_status"))
                            || "ERROR".equalsIgnoreCase(rs.getString("sunat_status")))
                    .build();

            SaleV2EditInfoResponse edit = SaleV2EditInfoResponse.builder()
                    .status(rs.getString("edit_status"))
                    .count(getInteger(rs, "edit_count"))
                    .lastEditedAt(rs.getTimestamp("last_edited_at") != null
                            ? rs.getTimestamp("last_edited_at").toLocalDateTime()
                            : null)
                    .lastEditedBy(getLong(rs, "last_edited_by"))
                    .lastEditedByUsername(rs.getString("last_edited_by_username"))
                    .lastEditReason(rs.getString("last_edit_reason"))
                    .build();

            return SaleV2DetailResponse.builder()
                    .saleId(rs.getLong("sale_id"))
                    .stationId(getLong(rs, "station_id"))
                    .saleSessionId(getLong(rs, "sale_session_id"))
                    .createdBy(rs.getLong("created_by"))
                    .createdByUser(createdByUser)
                    .docType(rs.getString("doc_type"))
                    .series(rs.getString("series"))
                    .number(rs.getLong("number"))
                    .issueDate(rs.getDate("issue_date").toLocalDate())
                    .currency(rs.getString("currency"))
                    .exchangeRate(rs.getBigDecimal("exchange_rate"))
                    .priceList(rs.getString("price_list"))
                    .customerId(getLong(rs, "customer_id"))
                    .customerDocType(rs.getString("customer_doc_type"))
                    .customerDocNumber(rs.getString("customer_doc_number"))
                    .customerName(rs.getString("customer_name"))
                    .customerAddress(rs.getString("customer_address"))
                    .customerUbigeo(rs.getString("customer_ubigeo"))
                    .customerDepartment(rs.getString("customer_department"))
                    .customerProvince(rs.getString("customer_province"))
                    .customerDistrict(rs.getString("customer_district"))
                    .taxStatus(rs.getString("tax_status"))
                    .taxReason(rs.getString("tax_reason"))
                    .igvRate(rs.getBigDecimal("igv_rate"))
                    .igvIncluded(rs.getObject("igv_included", Boolean.class))
                    .paymentType(rs.getString("payment_type"))
                    .creditDays(getInteger(rs, "credit_days"))
                    .dueDate(rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null)
                    .subtotal(rs.getBigDecimal("subtotal"))
                    .discountTotal(rs.getBigDecimal("discount_total"))
                    .igvAmount(rs.getBigDecimal("igv_amount"))
                    .total(rs.getBigDecimal("total"))
                    .giftCostTotal(rs.getBigDecimal("gift_cost_total"))
                    .notes(rs.getString("notes"))
                    .status(rs.getString("status"))
                    .contractId(getLong(rs, "contract_id"))
                    .reference(reference)
                    .sunat(sunat)
                    .edit(edit)
                    .relation(buildRelationInfo(rs))
                    .createdAt(rs.getTimestamp("created_at") != null
                            ? rs.getTimestamp("created_at").toLocalDateTime()
                            : null)
                    .updatedAt(rs.getTimestamp("updated_at") != null
                            ? rs.getTimestamp("updated_at").toLocalDateTime()
                            : null)
                    .build();
        };

        return jdbcClient.sql(sql)
                .param(saleId)
                .query(mapper)
                .optional()
                .orElse(null);
    }

    @Override
    public List<SaleV2ItemResponse> findSaleItems(Long saleId) {
        String sql = """
        SELECT
            si.id AS sale_item_id,
            si.line_number AS line_number,
            si.product_id AS product_id,
            si.sku AS sku,
            si.description AS description,
            si.presentation AS presentation,
            si.factor AS factor,
            si.quantity AS quantity,
            si.unit_price AS unit_price,
            si.discount_percent AS discount_percent,
            si.discount_amount AS discount_amount,
            si.line_kind AS line_kind,
            si.gift_reason AS gift_reason,
            si.facturable_sunat AS facturable_sunat,
            si.affects_stock AS affects_stock,
            si.visible_in_document AS visible_in_document,
            si.unit_cost_snapshot AS unit_cost_snapshot,
            si.total_cost_snapshot AS total_cost_snapshot,
            si.revenue_total AS revenue_total,
            si.created_at AS created_at,

            p.category AS product_category,

            psu.id AS serial_unit_id,
            psu.status AS serial_unit_status,

            p.brand AS v_marca,
            psu.color AS v_color,
            p.model AS v_modelo,
            psu.engine_number AS v_num_motor,
            psu.chassis_number AS v_num_chasis,
            psu.vin AS v_num_vin,
            psu.dua_number AS v_dua,
            psu.dua_item AS v_item_dua,
            psu.year_make AS v_anio_fabricacion,

            vs.engine_capacity AS v_engine_capacity,
            vs.fuel AS v_combustible,
            vs.cylinders AS v_num_cilindros,
            vs.net_weight AS v_peso_neto,
            vs.gross_weight AS v_peso_bruto,
            vs.vehicle_class AS v_clase,
            vs.bodywork AS v_carroceria,
            vs.engine_power AS v_potencia_motor,
            vs.rolling_form AS v_forma_rodante,
            vs.seats AS v_num_asientos,
            vs.passengers AS v_num_pasajeros,
            vs.axles AS v_num_ejes,
            vs.wheels AS v_num_ruedas,
            vs.payload AS v_carga_util,
            vs.length AS v_largo,
            vs.width AS v_ancho,
            vs.height AS v_alto

          FROM sale_item si
          JOIN sale s ON s.id = si.sale_id
          JOIN product p ON p.id = si.product_id
          LEFT JOIN contract_item ci
                 ON s.contract_id IS NOT NULL
                AND ci.contract_id = s.contract_id
                AND ci.product_id = si.product_id
          LEFT JOIN product_serial_unit psu
                 ON psu.sale_item_id = si.id
                 OR psu.id = ci.serial_unit_id
          LEFT JOIN product_vehicle_specs vs ON vs.product_id = p.id
         WHERE si.sale_id = ?
         ORDER BY si.line_number ASC
        """;

        RowMapper<SaleV2ItemResponse> mapper = (rs, rowNum) -> {
            String category = rs.getString("product_category");
            String cat = category != null ? category.trim().toUpperCase() : null;

            boolean isVehicle = "MOTOR".equals(cat) || "MOTOCICLETAS".equals(cat) || "MOTOCICLETA".equals(cat);

            VehicleDetailsResponse vehicleDetails = null;

            if (isVehicle) {
                String engineNumber = rs.getString("v_num_motor");
                if (engineNumber != null && !engineNumber.trim().isEmpty()) {
                    String cap = formatEngineCapacity(rs.getString("v_engine_capacity"));

                    vehicleDetails = VehicleDetailsResponse.builder()
                            .marca(rs.getString("v_marca"))
                            .color(rs.getString("v_color"))
                            .modelo(rs.getString("v_modelo"))
                            .numMotor(rs.getString("v_num_motor"))
                            .numChasis(rs.getString("v_num_chasis"))
                            .numVin(rs.getString("v_num_vin"))
                            .dua(rs.getString("v_dua"))
                            .itemDua(rs.getObject("v_item_dua", Integer.class))
                            .anioFabricacion(rs.getObject("v_anio_fabricacion", Integer.class))
                            .capacidadMotor(cap)
                            .combustible(rs.getString("v_combustible"))
                            .numCilindros(rs.getObject("v_num_cilindros", Integer.class))
                            .pesoNeto(rs.getBigDecimal("v_peso_neto"))
                            .pesoBruto(rs.getBigDecimal("v_peso_bruto"))
                            .clase(rs.getString("v_clase"))
                            .carroceria(rs.getString("v_carroceria"))
                            .potenciaMotor(rs.getString("v_potencia_motor"))
                            .formaRodante(rs.getString("v_forma_rodante"))
                            .numAsientos(rs.getObject("v_num_asientos", Integer.class))
                            .numPasajeros(rs.getObject("v_num_pasajeros", Integer.class))
                            .numEjes(rs.getObject("v_num_ejes", Integer.class))
                            .numRuedas(rs.getObject("v_num_ruedas", Integer.class))
                            .cargaUtil(rs.getBigDecimal("v_carga_util"))
                            .largo(rs.getBigDecimal("v_largo"))
                            .ancho(rs.getBigDecimal("v_ancho"))
                            .alto(rs.getBigDecimal("v_alto"))
                            .build();
                }
            }

            return SaleV2ItemResponse.builder()
                    .saleItemId(rs.getLong("sale_item_id"))
                    .lineNumber(rs.getInt("line_number"))
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
                    .facturableSunat(rs.getBoolean("facturable_sunat"))
                    .affectsStock(rs.getBoolean("affects_stock"))
                    .visibleInDocument(rs.getBoolean("visible_in_document"))
                    .unitCostSnapshot(rs.getBigDecimal("unit_cost_snapshot"))
                    .totalCostSnapshot(rs.getBigDecimal("total_cost_snapshot"))
                    .revenueTotal(rs.getBigDecimal("revenue_total"))
                    .productCategory(category)
                    .serialUnitId(rs.getObject("serial_unit_id") != null ? rs.getLong("serial_unit_id") : null)
                    .serialUnitStatus(rs.getString("serial_unit_status"))
                    .createdAt(rs.getTimestamp("created_at") != null
                            ? rs.getTimestamp("created_at").toLocalDateTime()
                            : null)
                    .vehicleDetails(vehicleDetails)
                    .build();
        };

        return jdbcClient.sql(sql)
                .param(saleId)
                .query(mapper)
                .list();
    }

    private static String formatEngineCapacity(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        return v.toUpperCase().contains("CC") ? v : v + "CC";
    }

    @Override
    public SaleV2PaymentResponse findSalePayment(Long saleId) {
        String sql = """
            SELECT method, amount, paid_at
              FROM sale_payment
             WHERE sale_id = ?
        """;
        RowMapper<SaleV2PaymentResponse> mapper = (rs, rowNum) -> SaleV2PaymentResponse.builder()
                .method(rs.getString("method"))
                .amount(rs.getBigDecimal("amount"))
                .paidAt(rs.getTimestamp("paid_at").toLocalDateTime())
                .build();

        return jdbcClient.sql(sql)
                .param(saleId)
                .query(mapper)
                .optional()
                .orElse(null);
    }

    @Override
    public List<SaleV2CounterSaleAssociationResponse> findCounterSaleAssociations(Long saleId) {
        String sql = """
            SELECT cs.id AS counter_sale_id,
                   cs.series AS series,
                   cs.number AS number,
                   cs.total AS total,
                   cs.associated_doc_type AS associated_doc_type,
                   cs.associated_series AS associated_series,
                   cs.associated_number AS associated_number,
                   cs.associated_at AS associated_at
              FROM counter_sale cs
             WHERE cs.associated_sale_id = ?
               AND COALESCE(cs.associated_to_sunat, FALSE) = TRUE
             ORDER BY cs.associated_at ASC NULLS LAST, cs.id ASC
        """;

        RowMapper<SaleV2CounterSaleAssociationResponse> mapper = (rs, rowNum) -> SaleV2CounterSaleAssociationResponse.builder()
                .counterSaleId(rs.getLong("counter_sale_id"))
                .series(rs.getString("series"))
                .number(getLong(rs, "number"))
                .total(rs.getBigDecimal("total"))
                .associatedDocType(rs.getString("associated_doc_type"))
                .associatedSeries(rs.getString("associated_series"))
                .associatedNumber(getLong(rs, "associated_number"))
                .associatedAt(rs.getTimestamp("associated_at") != null ? rs.getTimestamp("associated_at").toLocalDateTime() : null)
                .build();

        return jdbcClient.sql(sql)
                .param(saleId)
                .query(mapper)
                .list();
    }

    @Override
    public AccountsReceivableInfoResponse findReceivableBySaleId(Long saleId) {
        String sql = """
            SELECT
                ar.id AS ar_id,
                ar.issue_date AS issue_date,
                ar.due_date AS due_date,
                ar.total_amount AS total_amount,
                ar.paid_amount AS paid_amount,
                ar.balance_amount AS balance_amount,
                ar.status AS status
              FROM accounts_receivable ar
             WHERE ar.sale_id = ?
        """;

        RowMapper<AccountsReceivableInfoResponse> mapper = (rs, rowNum) -> AccountsReceivableInfoResponse.builder()
                .arId(rs.getLong("ar_id"))
                .issueDate(rs.getDate("issue_date").toLocalDate())
                .dueDate(rs.getDate("due_date").toLocalDate())
                .totalAmount(rs.getBigDecimal("total_amount"))
                .paidAmount(rs.getBigDecimal("paid_amount"))
                .balanceAmount(rs.getBigDecimal("balance_amount"))
                .status(rs.getString("status"))
                .build();

        return jdbcClient.sql(sql)
                .param(saleId)
                .query(mapper)
                .optional()
                .orElse(null);
    }

    @Override
    public List<AccountsReceivablePaymentInfo> findReceivablePayments(Long arId) {
        String sql = """
            SELECT id, amount, method, paid_at, note
              FROM accounts_receivable_payment
             WHERE ar_id = ?
             ORDER BY paid_at ASC, id ASC
        """;
        RowMapper<AccountsReceivablePaymentInfo> mapper = (rs, rowNum) -> AccountsReceivablePaymentInfo.builder()
                .id(rs.getLong("id"))
                .amount(rs.getBigDecimal("amount"))
                .method(rs.getString("method"))
                .paidAt(rs.getTimestamp("paid_at").toLocalDateTime())
                .note(rs.getString("note"))
                .build();

        return jdbcClient.sql(sql)
                .param(arId)
                .query(mapper)
                .list();
    }
}

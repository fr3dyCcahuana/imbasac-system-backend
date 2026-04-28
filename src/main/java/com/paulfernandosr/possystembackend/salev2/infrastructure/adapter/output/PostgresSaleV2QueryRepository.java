package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2QueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
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
                                          String paymentType) {
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
                || "RECHAZADO".equals(normalizedSunatStatus));
    }

    private String buildFullName(String firstName, String lastName) {
        String fullName = ((firstName == null ? "" : firstName.trim()) + " "
                + (lastName == null ? "" : lastName.trim())).trim();
        return fullName.isBlank() ? null : fullName;
    }

    @Override
    public long countSales(String likeParam,
                           String docType,
                           String series,
                           Long number,
                           String status,
                           String sunatStatus,
                           String editStatus,
                           String paymentType) {
        StringBuilder sql = new StringBuilder("""
        SELECT COUNT(1)
          FROM sale s
         WHERE 1 = 1
    """);

        List<Object> params = new ArrayList<>();
        appendCommonSalesFilters(sql, params, likeParam, docType, series, number, status, sunatStatus, editStatus, paymentType);

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
            s.updated_at AS updated_at
          FROM sale s
          LEFT JOIN users u_edit
                 ON u_edit.id = s.last_edited_by
         WHERE 1 = 1
    """);

        List<Object> params = new ArrayList<>();
        appendCommonSalesFilters(sql, params, likeParam, docType, series, number, status, sunatStatus, editStatus, paymentType);

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
                    .editCount((Integer) rs.getObject("edit_count"))
                    .lastEditedAt(rs.getTimestamp("last_edited_at") != null ? rs.getTimestamp("last_edited_at").toLocalDateTime() : null)
                    .lastEditedByUsername(rs.getString("last_edited_by_username"))
                    .canEditBeforeSunat(isEditableForList(saleStatusValue, sunatStatusValue))
                    .canEmitSunat(isEmittableForList(saleStatusValue, docTypeValue, sunatStatusValue))
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

                sr.proforma_id    AS reference_proforma_id,
                sr.imported_at    AS reference_imported_at,

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
              LEFT JOIN users u_created
                     ON u_created.id = s.created_by
              LEFT JOIN users u_edit
                     ON u_edit.id = s.last_edited_by
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
                    .build();

            SaleV2EditInfoResponse edit = SaleV2EditInfoResponse.builder()
                    .status(rs.getString("edit_status"))
                    .count((Integer) rs.getObject("edit_count"))
                    .lastEditedAt(rs.getTimestamp("last_edited_at") != null
                            ? rs.getTimestamp("last_edited_at").toLocalDateTime()
                            : null)
                    .lastEditedBy((Long) rs.getObject("last_edited_by"))
                    .lastEditedByUsername(rs.getString("last_edited_by_username"))
                    .lastEditReason(rs.getString("last_edit_reason"))
                    .build();

            return SaleV2DetailResponse.builder()
                    .saleId(rs.getLong("sale_id"))
                    .stationId(rs.getLong("station_id"))
                    .saleSessionId((Long) rs.getObject("sale_session_id"))
                    .createdBy(rs.getLong("created_by"))
                    .createdByUser(createdByUser)
                    .docType(rs.getString("doc_type"))
                    .series(rs.getString("series"))
                    .number(rs.getLong("number"))
                    .issueDate(rs.getDate("issue_date").toLocalDate())
                    .currency(rs.getString("currency"))
                    .exchangeRate(rs.getBigDecimal("exchange_rate"))
                    .priceList(rs.getString("price_list"))
                    .customerId((Long) rs.getObject("customer_id"))
                    .customerDocType(rs.getString("customer_doc_type"))
                    .customerDocNumber(rs.getString("customer_doc_number"))
                    .customerName(rs.getString("customer_name"))
                    .customerAddress(rs.getString("customer_address"))
                    .taxStatus(rs.getString("tax_status"))
                    .taxReason(rs.getString("tax_reason"))
                    .igvRate(rs.getBigDecimal("igv_rate"))
                    .igvIncluded(rs.getObject("igv_included", Boolean.class))
                    .paymentType(rs.getString("payment_type"))
                    .creditDays((Integer) rs.getObject("credit_days"))
                    .dueDate(rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null)
                    .subtotal(rs.getBigDecimal("subtotal"))
                    .discountTotal(rs.getBigDecimal("discount_total"))
                    .igvAmount(rs.getBigDecimal("igv_amount"))
                    .total(rs.getBigDecimal("total"))
                    .giftCostTotal(rs.getBigDecimal("gift_cost_total"))
                    .notes(rs.getString("notes"))
                    .status(rs.getString("status"))
                    .contractId((Long) rs.getObject("contract_id"))
                    .reference(reference)
                    .sunat(sunat)
                    .edit(edit)
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
            vs.gross_weight AS v_peso_bruto

          FROM sale_item si
          JOIN product p ON p.id = si.product_id
          LEFT JOIN product_serial_unit psu ON psu.sale_item_id = si.id
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
                    String cap = null;
                    var capVal = rs.getBigDecimal("v_engine_capacity");
                    if (capVal != null) {
                        cap = capVal.stripTrailingZeros().toPlainString() + "CC";
                    }

                    vehicleDetails = VehicleDetailsResponse.builder()
                            .marca(rs.getString("v_marca"))
                            .color(rs.getString("v_color"))
                            .modelo(rs.getString("v_modelo"))
                            .numMotor(rs.getString("v_num_motor"))
                            .numChasis(rs.getString("v_num_chasis"))
                            .numVin(rs.getString("v_num_vin"))
                            .dua(rs.getString("v_dua"))
                            .itemDua((Integer) rs.getObject("v_item_dua"))
                            .anioFabricacion((Integer) rs.getObject("v_anio_fabricacion"))
                            .capacidadMotor(cap)
                            .combustible(rs.getString("v_combustible"))
                            .numCilindros((Integer) rs.getObject("v_num_cilindros"))
                            .pesoNeto(rs.getBigDecimal("v_peso_neto"))
                            .pesoBruto(rs.getBigDecimal("v_peso_bruto"))
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
                    .serialUnitId((Long) rs.getObject("serial_unit_id"))
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
                .number((Long) rs.getObject("number"))
                .total(rs.getBigDecimal("total"))
                .associatedDocType(rs.getString("associated_doc_type"))
                .associatedSeries(rs.getString("associated_series"))
                .associatedNumber((Long) rs.getObject("associated_number"))
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
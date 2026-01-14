package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2QueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresSaleV2QueryRepository implements SaleV2QueryRepository {

    private final JdbcClient jdbcClient;

    @Override
    public long countSales(String likeParam) {
        String sql = """
            SELECT COUNT(1)
              FROM sale s
             WHERE s.series ILIKE ?
                OR CAST(s.number AS TEXT) ILIKE ?
                OR COALESCE(s.customer_name,'') ILIKE ?
                OR COALESCE(s.customer_doc_number,'') ILIKE ?
                OR (s.doc_type || ' ' || s.series || '-' || s.number) ILIKE ?
        """;
        return jdbcClient.sql(sql)
                .params(likeParam, likeParam, likeParam, likeParam, likeParam)
                .query(Long.class)
                .single();
    }

    @Override
    public List<SaleV2SummaryResponse> findSalesPage(String likeParam, int limit, int offset) {
        String sql = """
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
                s.status      AS status
              FROM sale s
             WHERE s.series ILIKE ?
                OR CAST(s.number AS TEXT) ILIKE ?
                OR COALESCE(s.customer_name,'') ILIKE ?
                OR COALESCE(s.customer_doc_number,'') ILIKE ?
                OR (s.doc_type || ' ' || s.series || '-' || s.number) ILIKE ?
             ORDER BY s.issue_date DESC, s.id DESC
             LIMIT ?
            OFFSET ?
        """;

        RowMapper<SaleV2SummaryResponse> mapper = (rs, rowNum) -> SaleV2SummaryResponse.builder()
                .saleId(rs.getLong("sale_id"))
                .docType(rs.getString("doc_type"))
                .series(rs.getString("series"))
                .number(rs.getLong("number"))
                .issueDate(rs.getDate("issue_date").toLocalDate())
                .customerDocNumber(rs.getString("customer_doc_number"))
                .customerName(rs.getString("customer_name"))
                .paymentType(rs.getString("payment_type"))
                .total(rs.getBigDecimal("total"))
                .status(rs.getString("status"))
                .build();

        return jdbcClient.sql(sql)
                .params(likeParam, likeParam, likeParam, likeParam, likeParam, limit, offset)
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
                s.payment_type    AS payment_type,
                s.credit_days     AS credit_days,
                s.due_date        AS due_date,
                s.subtotal        AS subtotal,
                s.discount_total  AS discount_total,
                s.igv_amount      AS igv_amount,
                s.total           AS total,
                s.gift_cost_total AS gift_cost_total,
                s.notes           AS notes,
                s.status          AS status
              FROM sale s
             WHERE s.id = ?
        """;

        RowMapper<SaleV2DetailResponse> mapper = (rs, rowNum) -> SaleV2DetailResponse.builder()
                .saleId(rs.getLong("sale_id"))
                .stationId(rs.getLong("station_id"))
                .saleSessionId((Long) rs.getObject("sale_session_id"))
                .createdBy(rs.getLong("created_by"))
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
                .build();

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
                si.revenue_total AS revenue_total
              FROM sale_item si
             WHERE si.sale_id = ?
             ORDER BY si.line_number ASC
        """;

        RowMapper<SaleV2ItemResponse> mapper = (rs, rowNum) -> SaleV2ItemResponse.builder()
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
                .build();

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

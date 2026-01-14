package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.AccountsReceivableQueryRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivableDetailResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentInfo;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivableSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresAccountsReceivableQueryRepository implements AccountsReceivableQueryRepository {

    private final JdbcClient jdbcClient;

    @Override
    public long countPage(Long customerId, String status, String likeParam) {
        String lp = (likeParam == null || likeParam.isBlank()) ? "%%" : likeParam;

        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(1)
              FROM accounts_receivable ar
              JOIN sale s ON s.id = ar.sale_id
             WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (customerId != null) {
            sql.append(" AND ar.customer_id = ? ");
            params.add(customerId);
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND ar.status = ? ");
            params.add(status);
        }

        // Si lp = "%%" no filtra en la práctica, pero mantiene la lógica uniforme.
        sql.append("""
            AND (
                 COALESCE(s.customer_name,'') ILIKE ?
              OR COALESCE(s.customer_doc_number,'') ILIKE ?
              OR (s.doc_type || ' ' || s.series || '-' || s.number) ILIKE ?
            )
        """);
        params.add(lp);
        params.add(lp);
        params.add(lp);

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query(Long.class)
                .single();
    }

    @Override
    public List<AccountsReceivableSummaryResponse> findPage(Long customerId, String status, String likeParam, int limit, int offset) {
        String lp = (likeParam == null || likeParam.isBlank()) ? "%%" : likeParam;

        StringBuilder sql = new StringBuilder("""
            SELECT
                ar.id AS ar_id,
                ar.sale_id AS sale_id,
                s.doc_type AS sale_doc_type,
                s.series AS sale_series,
                s.number AS sale_number,
                ar.customer_id AS customer_id,
                s.customer_doc_number AS customer_doc_number,
                s.customer_name AS customer_name,
                ar.issue_date AS issue_date,
                ar.due_date AS due_date,
                ar.total_amount AS total_amount,
                ar.paid_amount AS paid_amount,
                ar.balance_amount AS balance_amount,
                ar.status AS status
              FROM accounts_receivable ar
              JOIN sale s ON s.id = ar.sale_id
             WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (customerId != null) {
            sql.append(" AND ar.customer_id = ? ");
            params.add(customerId);
        }

        if (status != null && !status.isBlank()) {
            sql.append(" AND ar.status = ? ");
            params.add(status);
        }

        sql.append("""
            AND (
                 COALESCE(s.customer_name,'') ILIKE ?
              OR COALESCE(s.customer_doc_number,'') ILIKE ?
              OR (s.doc_type || ' ' || s.series || '-' || s.number) ILIKE ?
            )
             ORDER BY ar.due_date ASC, ar.id DESC
             LIMIT ?
            OFFSET ?
        """);

        params.add(lp);
        params.add(lp);
        params.add(lp);
        params.add(limit);
        params.add(offset);

        RowMapper<AccountsReceivableSummaryResponse> mapper = (rs, rowNum) -> AccountsReceivableSummaryResponse.builder()
                .arId(rs.getLong("ar_id"))
                .saleId(rs.getLong("sale_id"))
                .saleDocType(rs.getString("sale_doc_type"))
                .saleSeries(rs.getString("sale_series"))
                .saleNumber(rs.getLong("sale_number"))
                .customerId(rs.getLong("customer_id"))
                .customerDocNumber(rs.getString("customer_doc_number"))
                .customerName(rs.getString("customer_name"))
                .issueDate(rs.getDate("issue_date").toLocalDate())
                .dueDate(rs.getDate("due_date").toLocalDate())
                .totalAmount(rs.getBigDecimal("total_amount"))
                .paidAmount(rs.getBigDecimal("paid_amount"))
                .balanceAmount(rs.getBigDecimal("balance_amount"))
                .status(rs.getString("status"))
                .build();

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query(mapper)
                .list();
    }

    @Override
    public AccountsReceivableDetailResponse findById(Long arId) {
        String headerSql = """
            SELECT
                ar.id AS ar_id,
                ar.sale_id AS sale_id,
                s.doc_type AS sale_doc_type,
                s.series AS sale_series,
                s.number AS sale_number,
                ar.customer_id AS customer_id,
                s.customer_doc_number AS customer_doc_number,
                s.customer_name AS customer_name,
                ar.issue_date AS issue_date,
                ar.due_date AS due_date,
                ar.total_amount AS total_amount,
                ar.paid_amount AS paid_amount,
                ar.balance_amount AS balance_amount,
                ar.status AS status
              FROM accounts_receivable ar
              JOIN sale s ON s.id = ar.sale_id
             WHERE ar.id = ?
        """;

        RowMapper<AccountsReceivableDetailResponse> headerMapper = (rs, rowNum) -> AccountsReceivableDetailResponse.builder()
                .arId(rs.getLong("ar_id"))
                .saleId(rs.getLong("sale_id"))
                .saleDocType(rs.getString("sale_doc_type"))
                .saleSeries(rs.getString("sale_series"))
                .saleNumber(rs.getLong("sale_number"))
                .customerId(rs.getLong("customer_id"))
                .customerDocNumber(rs.getString("customer_doc_number"))
                .customerName(rs.getString("customer_name"))
                .issueDate(rs.getDate("issue_date").toLocalDate())
                .dueDate(rs.getDate("due_date").toLocalDate())
                .totalAmount(rs.getBigDecimal("total_amount"))
                .paidAmount(rs.getBigDecimal("paid_amount"))
                .balanceAmount(rs.getBigDecimal("balance_amount"))
                .status(rs.getString("status"))
                .build();

        AccountsReceivableDetailResponse header = jdbcClient.sql(headerSql)
                .param(arId)
                .query(headerMapper)
                .optional()
                .orElse(null);

        if (header == null) return null;

        String paymentsSql = """
            SELECT id, amount, method, paid_at, note
              FROM accounts_receivable_payment
             WHERE ar_id = ?
             ORDER BY paid_at ASC, id ASC
        """;

        RowMapper<AccountsReceivablePaymentInfo> paymentMapper = (rs, rowNum) -> AccountsReceivablePaymentInfo.builder()
                .id(rs.getLong("id"))
                .amount(rs.getBigDecimal("amount"))
                .method(rs.getString("method"))
                .paidAt(rs.getTimestamp("paid_at").toLocalDateTime())
                .note(rs.getString("note"))
                .build();

        List<AccountsReceivablePaymentInfo> payments = jdbcClient.sql(paymentsSql)
                .param(arId)
                .query(paymentMapper)
                .list();

        header.setPayments(payments);
        return header;
    }
}

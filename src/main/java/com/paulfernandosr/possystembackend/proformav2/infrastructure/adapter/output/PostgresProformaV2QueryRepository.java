package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaV2QueryRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaCreatorResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaQueryFilters;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresProformaV2QueryRepository implements ProformaV2QueryRepository {

    private final JdbcClient jdbcClient;

    @Override
    public long countPage(ProformaQueryFilters filters) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(1)
              FROM proforma p
             WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, filters);

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query(Long.class)
                .single();
    }

    @Override
    public List<ProformaV2SummaryResponse> findPage(ProformaQueryFilters filters, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                p.id AS proforma_id,
                p.series AS series,
                p.number AS number,
                p.issue_date AS issue_date,
                p.customer_doc_number AS customer_doc_number,
                p.customer_name AS customer_name,
                p.payment_type AS payment_type,
                p.credit_days AS credit_days,
                p.due_date AS due_date,
                p.total AS total,
                p.status AS status,
                p.converted_sale_id AS converted_sale_id,
                p.created_by AS created_by,
                u.first_name AS cb_first,
                u.last_name AS cb_last,
                u.username AS cb_username,
                (p.edited_at IS NOT NULL) AS edited,
                s.doc_type AS sale_doc_type,
                s.series AS sale_series,
                s.number AS sale_number
              FROM proforma p
              LEFT JOIN users u ON u.id = p.created_by
              LEFT JOIN sale s ON s.id = p.converted_sale_id
             WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, filters);

        sql.append("""
             ORDER BY p.issue_date DESC, p.id DESC
             LIMIT ?
            OFFSET ?
        """);
        params.add(limit);
        params.add(offset);

        RowMapper<ProformaV2SummaryResponse> mapper = (rs, rowNum) -> ProformaV2SummaryResponse.builder()
                .proformaId(rs.getLong("proforma_id"))
                .docType("PROFORMA")
                .series(rs.getString("series"))
                .number(rs.getLong("number"))
                .issueDate(rs.getDate("issue_date").toLocalDate())
                .customerDocNumber(rs.getString("customer_doc_number"))
                .customerName(rs.getString("customer_name"))
                .paymentType(rs.getString("payment_type"))
                .creditDays((Integer) rs.getObject("credit_days"))
                .dueDate(rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null)
                .total(rs.getBigDecimal("total"))
                .status(rs.getString("status"))
                .convertedSaleId((Long) rs.getObject("converted_sale_id"))
                .createdBy((Long) rs.getObject("created_by"))
                .createdByName(displayName(rs.getString("cb_first"), rs.getString("cb_last"), rs.getString("cb_username")))
                .edited(rs.getBoolean("edited"))
                .saleDocType(rs.getString("sale_doc_type"))
                .saleSeries(rs.getString("sale_series"))
                .saleNumber((Long) rs.getObject("sale_number"))
                .build();

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query(mapper)
                .list();
    }

    @Override
    public List<ProformaCreatorResponse> findCreators() {
        String sql = """
            SELECT u.id AS id, u.first_name AS cb_first, u.last_name AS cb_last, u.username AS cb_username
              FROM users u
             WHERE EXISTS (SELECT 1 FROM proforma p WHERE p.created_by = u.id)
             ORDER BY u.first_name, u.last_name, u.username
            """;
        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> ProformaCreatorResponse.builder()
                        .id(rs.getLong("id"))
                        .name(displayName(rs.getString("cb_first"), rs.getString("cb_last"), rs.getString("cb_username")))
                        .build())
                .list();
    }

    private void applyFilters(StringBuilder sql, List<Object> params, ProformaQueryFilters f) {
        if (f.status() != null && !f.status().isBlank()) {
            sql.append(" AND p.status = ? ");
            params.add(f.status());
        }

        String lp = (f.like() == null || f.like().isBlank()) ? "%%" : f.like();
        sql.append("""
            AND (
                 p.series ILIKE ?
              OR CAST(p.number AS TEXT) ILIKE ?
              OR COALESCE(p.customer_name,'') ILIKE ?
              OR COALESCE(p.customer_doc_number,'') ILIKE ?
              OR ('PROFORMA ' || p.series || '-' || p.number) ILIKE ?
            )
        """);
        params.add(lp);
        params.add(lp);
        params.add(lp);
        params.add(lp);
        params.add(lp);

        if (f.createdBy() != null) {
            sql.append(" AND p.created_by = ? ");
            params.add(f.createdBy());
        }
        if (f.edited() != null) {
            sql.append(Boolean.TRUE.equals(f.edited()) ? " AND p.edited_at IS NOT NULL " : " AND p.edited_at IS NULL ");
        }
        if (f.paymentType() != null && !f.paymentType().isBlank()) {
            sql.append(" AND p.payment_type = ? ");
            params.add(f.paymentType());
        }
        if (f.dateFrom() != null) {
            sql.append(" AND p.issue_date >= ? ");
            params.add(Date.valueOf(f.dateFrom()));
        }
        if (f.dateTo() != null) {
            sql.append(" AND p.issue_date <= ? ");
            params.add(Date.valueOf(f.dateTo()));
        }
    }

    private static String displayName(String firstName, String lastName, String username) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        return username == null ? "" : username;
    }
}

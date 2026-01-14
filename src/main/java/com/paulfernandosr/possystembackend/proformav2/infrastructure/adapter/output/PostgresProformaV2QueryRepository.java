package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaV2QueryRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresProformaV2QueryRepository implements ProformaV2QueryRepository {

    private final JdbcClient jdbcClient;

    @Override
    public long countPage(String status, String likeParam) {
        String lp = (likeParam == null || likeParam.isBlank()) ? "%%" : likeParam;

        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(1)
              FROM proforma p
             WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ? ");
            params.add(status);
        }

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

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query(Long.class)
                .single();
    }

    @Override
    public List<ProformaV2SummaryResponse> findPage(String status, String likeParam, int limit, int offset) {
        String lp = (likeParam == null || likeParam.isBlank()) ? "%%" : likeParam;

        StringBuilder sql = new StringBuilder("""
            SELECT
                p.id AS proforma_id,
                p.series AS series,
                p.number AS number,
                p.issue_date AS issue_date,
                p.customer_doc_number AS customer_doc_number,
                p.customer_name AS customer_name,
                p.total AS total,
                p.status AS status
              FROM proforma p
             WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ? ");
            params.add(status);
        }

        sql.append("""
            AND (
                 p.series ILIKE ?
              OR CAST(p.number AS TEXT) ILIKE ?
              OR COALESCE(p.customer_name,'') ILIKE ?
              OR COALESCE(p.customer_doc_number,'') ILIKE ?
              OR ('PROFORMA ' || p.series || '-' || p.number) ILIKE ?
            )
             ORDER BY p.issue_date DESC, p.id DESC
             LIMIT ?
            OFFSET ?
        """);

        params.add(lp);
        params.add(lp);
        params.add(lp);
        params.add(lp);
        params.add(lp);
        params.add(limit);
        params.add(offset);

        RowMapper<ProformaV2SummaryResponse> mapper = (rs, rowNum) -> ProformaV2SummaryResponse.builder()
                .proformaId(rs.getLong("proforma_id"))
                // Si tu DTO exige docType, fija literal "PROFORMA"
                .docType("PROFORMA")
                .series(rs.getString("series"))
                .number(rs.getLong("number"))
                .issueDate(rs.getDate("issue_date").toLocalDate())
                .customerDocNumber(rs.getString("customer_doc_number"))
                .customerName(rs.getString("customer_name"))
                .total(rs.getBigDecimal("total"))
                .status(rs.getString("status"))
                .build();

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query(mapper)
                .list();
    }
}

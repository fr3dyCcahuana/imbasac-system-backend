package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper.ProformaRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresProformaRepository implements ProformaRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Proforma create(Proforma proforma) {
        String sql = """
            INSERT INTO proforma(
              station_id, created_by,
              series, number, issue_date,
              price_list, currency,
              customer_id, customer_doc_type, customer_doc_number, customer_name, customer_address,
              notes,
              subtotal, discount_total, total,
              status,
              created_at, updated_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, NOW(), NOW())
            RETURNING *
            """;

        return jdbcClient.sql(sql)
                .params(
                        proforma.getStationId(),
                        proforma.getCreatedBy(),
                        proforma.getSeries(),
                        proforma.getNumber(),
                        java.sql.Date.valueOf(proforma.getIssueDate()),
                        String.valueOf(proforma.getPriceList()),
                        proforma.getCurrency(),
                        proforma.getCustomerId(),
                        proforma.getCustomerDocType(),
                        proforma.getCustomerDocNumber(),
                        proforma.getCustomerName(),
                        proforma.getCustomerAddress(),
                        proforma.getNotes(),
                        proforma.getSubtotal(),
                        proforma.getDiscountTotal(),
                        proforma.getTotal(),
                        proforma.getStatus().name()
                )
                .query(new ProformaRowMapper())
                .single();
    }

    @Override
    public Optional<Proforma> lockById(Long proformaId) {
        String sql = """
            SELECT *
            FROM proforma
            WHERE id = ?
            FOR UPDATE
            """;
        return jdbcClient.sql(sql)
                .param(proformaId)
                .query(new ProformaRowMapper())
                .optional();
    }

    @Override
    public Optional<Proforma> findById(Long proformaId) {
        String sql = """
            SELECT *
            FROM proforma
            WHERE id = ?
            """;
        return jdbcClient.sql(sql)
                .param(proformaId)
                .query(new ProformaRowMapper())
                .optional();
    }

    @Override
    public void updateStatus(Long proformaId, String status) {
        String sql = """
            UPDATE proforma
            SET status = ?, updated_at = NOW()
            WHERE id = ?
            """;
        jdbcClient.sql(sql).params(status, proformaId).update();
    }

    @Override
    public void touchUpdatedAt(Long proformaId) {
        String sql = """
            UPDATE proforma
            SET updated_at = NOW()
            WHERE id = ?
            """;
        jdbcClient.sql(sql).param(proformaId).update();
    }
}

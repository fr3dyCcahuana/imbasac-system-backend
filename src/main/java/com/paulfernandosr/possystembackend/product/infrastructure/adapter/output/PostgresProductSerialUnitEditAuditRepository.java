package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSerialUnitEditAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresProductSerialUnitEditAuditRepository implements ProductSerialUnitEditAuditRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insert(Long serialUnitId,
                       Long editedBy,
                       String editedByUsername,
                       String reason,
                       String beforeJson,
                       String afterJson) {

        String sql = """
            INSERT INTO product_serial_unit_edit_audit(
              serial_unit_id,
              edited_by,
              edited_by_username,
              reason,
              before_json,
              after_json,
              created_at
            ) VALUES (
              ?, ?, ?, ?,
              CAST(? AS jsonb),
              CAST(? AS jsonb),
              NOW()
            )
        """;

        jdbcClient.sql(sql)
                .params(serialUnitId, editedBy, editedByUsername, reason, beforeJson, afterJson)
                .update();
    }
}

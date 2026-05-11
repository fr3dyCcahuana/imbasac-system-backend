package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractEditAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractEditAuditRepository implements ContractEditAuditRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insert(Long contractId,
                       Long editedBy,
                       String editedByUsername,
                       String reason,
                       String beforeJson,
                       String afterJson) {

        String sql = """
            INSERT INTO contract_edit_audit(
              contract_id,
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
                .params(contractId, editedBy, editedByUsername, reason, beforeJson, afterJson)
                .update();
    }
}

package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractAccountsReceivableLookupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractAccountsReceivableLookupRepository implements ContractAccountsReceivableLookupRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Long findArIdBySaleId(Long saleId) {
        String sql = "SELECT id FROM accounts_receivable WHERE sale_id = ?";
        return jdbcClient.sql(sql)
                .param(saleId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }
}

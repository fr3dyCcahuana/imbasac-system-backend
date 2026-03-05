package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.port.output.SaleContractLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresSaleContractLinkRepository implements SaleContractLinkRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void linkSaleToContract(Long saleId, Long contractId) {
        String sql = "UPDATE sale SET contract_id = ? WHERE id = ?";
        jdbcClient.sql(sql)
                .params(contractId, saleId)
                .update();
    }
}

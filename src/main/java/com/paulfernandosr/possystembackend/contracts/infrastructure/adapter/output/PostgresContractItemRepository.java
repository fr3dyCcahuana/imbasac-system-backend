package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractItem;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractItemRepository implements ContractItemRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Long insert(ContractItem i) {
        String sql = """
            INSERT INTO contract_item(
              contract_id, product_id, serial_unit_id,
              sku, description, brand, model,
              unit_price,
              created_at
            ) VALUES (
              ?, ?, ?,
              ?, ?, ?, ?,
              ?,
              NOW()
            )
            RETURNING id
        """;

        return jdbcClient.sql(sql)
                .params(
                        i.getContractId(), i.getProductId(), i.getSerialUnitId(),
                        i.getSku(), i.getDescription(), i.getBrand(), i.getModel(),
                        i.getUnitPrice()
                )
                .query(Long.class)
                .single();
    }

    @Override
    public ContractItem findByContractId(Long contractId) {
        String sql = """
            SELECT id,
                   contract_id AS contractId,
                   product_id AS productId,
                   serial_unit_id AS serialUnitId,
                   sku,
                   description,
                   brand,
                   model,
                   unit_price AS unitPrice
              FROM contract_item
             WHERE contract_id = ?
        """;

        return jdbcClient.sql(sql)
                .param(contractId)
                .query(ContractItem.class)
                .optional()
                .orElse(null);
    }
}

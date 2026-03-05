package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractProductRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.ProductContractRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractProductRepository implements ContractProductRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ProductContractRow findById(Long productId) {
        String sql = """
            SELECT id,
                   sku,
                   name,
                   category,
                   manage_by_serial AS manageBySerial,
                   affects_stock AS affectsStock,
                   brand,
                   model
              FROM product
             WHERE id = ?
        """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(ProductContractRow.class)
                .optional()
                .orElse(null);
    }
}

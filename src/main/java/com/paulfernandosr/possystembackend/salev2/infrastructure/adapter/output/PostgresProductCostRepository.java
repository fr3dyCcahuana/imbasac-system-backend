package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.CostPolicy;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class PostgresProductCostRepository implements ProductCostRepository {

    private final JdbcClient jdbcClient;

    @Override
    public BigDecimal getUnitCost(Long productId, CostPolicy policy) {
        String col = (policy == CostPolicy.ULTIMO) ? "last_unit_cost" : "average_cost";

        String sql = "SELECT COALESCE(" + col + ", 0) FROM product_stock WHERE product_id = ?";

        return jdbcClient.sql(sql)
                .param(productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }
}

package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleSessionAccumulatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class PostgresSaleSessionAccumulatorRepository implements SaleSessionAccumulatorRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void addSaleIncomeAndDiscount(Long saleSessionId, BigDecimal saleTotal, BigDecimal discountTotal) {
        // Mantiene el módulo de sesiones sin refactor: solo acumula montos.
        String sql = """
            UPDATE sale_sessions
               SET sales_income   = COALESCE(sales_income, 0) + ?,
                   total_discount = COALESCE(total_discount, 0) + ?,
                   updated_at = NOW()
             WHERE id = ?
               AND closed_at IS NULL
        """;

        jdbcClient.sql(sql)
                .params(saleTotal, discountTotal, saleSessionId)
                .update();
    }

    @Override
    public void subtractSaleIncomeAndDiscount(Long saleSessionId, BigDecimal saleTotal, BigDecimal discountTotal) {
        String sql = """
            UPDATE sale_sessions
               SET sales_income   = COALESCE(sales_income, 0) - ?,
                   total_discount = COALESCE(total_discount, 0) - ?,
                   updated_at = NOW()
             WHERE id = ?
               AND closed_at IS NULL
        """;

        jdbcClient.sql(sql)
                .params(saleTotal, discountTotal, saleSessionId)
                .update();
    }
}

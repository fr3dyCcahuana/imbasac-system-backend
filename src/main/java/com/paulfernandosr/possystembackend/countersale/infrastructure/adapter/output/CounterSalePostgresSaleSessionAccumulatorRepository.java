package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.SaleSessionAccumulatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresSaleSessionAccumulatorRepository implements SaleSessionAccumulatorRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void addSaleIncomeAndDiscount(Long saleSessionId, BigDecimal saleTotal, BigDecimal discountTotal) {
        String sql = """
            UPDATE sale_sessions
               SET sales_income = COALESCE(sales_income, 0) + ?,
                   total_discount = COALESCE(total_discount, 0) + ?,
                   updated_at = NOW()
             WHERE id = ?
               AND closed_at IS NULL
        """;
        runOrFail(sql, saleSessionId, saleTotal, discountTotal);
    }

    @Override
    public void subtractSaleIncomeAndDiscount(Long saleSessionId, BigDecimal saleTotal, BigDecimal discountTotal) {
        String sql = """
            UPDATE sale_sessions
               SET sales_income = COALESCE(sales_income, 0) - ?,
                   total_discount = COALESCE(total_discount, 0) - ?,
                   updated_at = NOW()
             WHERE id = ?
               AND closed_at IS NULL
        """;
        runOrFail(sql, saleSessionId, saleTotal, discountTotal);
    }

    @Override
    public void addExpense(Long saleSessionId, BigDecimal amount) {
        String sql = """
            UPDATE sale_sessions
               SET total_expenses = COALESCE(total_expenses, 0) + ?,
                   updated_at = NOW()
             WHERE id = ?
               AND closed_at IS NULL
        """;
        int updated = jdbcClient.sql(sql).params(amount, saleSessionId).update();
        if (updated == 0) {
            throw new InvalidCounterSaleException("No se pudo registrar el egreso en la sesión de caja abierta: " + saleSessionId);
        }
    }

    private void runOrFail(String sql, Long saleSessionId, BigDecimal saleTotal, BigDecimal discountTotal) {
        int updated = jdbcClient.sql(sql)
                .params(saleTotal, discountTotal, saleSessionId)
                .update();
        if (updated == 0) {
            throw new InvalidCounterSaleException("No se pudo actualizar la sesión de caja abierta: " + saleSessionId);
        }
    }
}

package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.CustomerAccountSnapshot;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.CustomerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class PostgresCustomerAccountRepository implements CustomerAccountRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void ensureExists(Long customerId) {
        String sql = """
            INSERT INTO customer_account(customer_id)
            VALUES (?)
            ON CONFLICT (customer_id) DO NOTHING
        """;

        jdbcClient.sql(sql)
                .param(customerId)
                .update();
    }

    @Override
    public CustomerAccountSnapshot findByCustomerId(Long customerId) {
        String sql = """
            SELECT
              customer_id,
              credit_enabled,
              credit_limit,
              current_debt,
              overdue_debt,
              status
            FROM customer_account
            WHERE customer_id = ?
        """;

        return jdbcClient.sql(sql)
                .param(customerId)
                .query((rs, rowNum) -> CustomerAccountSnapshot.builder()
                        .customerId(rs.getLong("customer_id"))
                        .creditEnabled(rs.getBoolean("credit_enabled"))
                        .creditLimit(rs.getBigDecimal("credit_limit"))
                        .currentDebt(rs.getBigDecimal("current_debt"))
                        .overdueDebt(rs.getBigDecimal("overdue_debt"))
                        .status(rs.getString("status"))
                        .build())
                .optional()
                .orElse(null);
    }

    @Override
    public void touchLastSaleAt(Long customerId) {
        String sql = """
            UPDATE customer_account
               SET last_sale_at = NOW(),
                   updated_at = NOW()
             WHERE customer_id = ?
        """;

        jdbcClient.sql(sql)
                .param(customerId)
                .update();
    }

    @Override
    public void touchLastPaymentAt(Long customerId) {
        String sql = """
            UPDATE customer_account
               SET last_payment_at = NOW(),
                   updated_at = NOW()
             WHERE customer_id = ?
        """;

        jdbcClient.sql(sql)
                .param(customerId)
                .update();
    }

    @Override
    public void recalculate(Long customerId) {
        // status de morosidad basado en reglas:
        // - overdue_debt > 0 => MOROSO
        // - credit_enabled=false => BLOQUEADO
        // - else OK
        String sql = """
            WITH debt AS (
              SELECT
                COALESCE(SUM(CASE WHEN balance_amount > 0 THEN balance_amount ELSE 0 END), 0) AS current_debt,
                COALESCE(SUM(CASE WHEN balance_amount > 0 AND due_date < CURRENT_DATE THEN balance_amount ELSE 0 END), 0) AS overdue_debt
              FROM accounts_receivable
              WHERE customer_id = ?
                AND status IN ('PENDIENTE','VENCIDO')
            )
            UPDATE customer_account ca
               SET current_debt = (SELECT current_debt FROM debt),
                   overdue_debt = (SELECT overdue_debt FROM debt),
                   status = CASE
                     WHEN ca.credit_enabled = FALSE THEN 'BLOQUEADO'
                     WHEN (SELECT overdue_debt FROM debt) > 0 THEN 'MOROSO'
                     ELSE 'OK'
                   END,
                   updated_at = NOW()
             WHERE ca.customer_id = ?
        """;

        jdbcClient.sql(sql)
                .params(customerId, customerId)
                .update();
    }
}

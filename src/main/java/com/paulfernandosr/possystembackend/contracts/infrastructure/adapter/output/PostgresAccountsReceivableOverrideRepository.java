package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.port.output.AccountsReceivableOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class PostgresAccountsReceivableOverrideRepository implements AccountsReceivableOverrideRepository {

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

    @Override
    public void overrideTotals(Long arId, BigDecimal totalAmount, LocalDate dueDate) {

        String sql = """
            UPDATE accounts_receivable
               SET total_amount   = ?,
                   balance_amount = GREATEST(? - paid_amount, 0),
                   due_date       = ?,
                   status         = CASE
                                      WHEN GREATEST(? - paid_amount, 0) = 0 THEN 'PAGADO'
                                      WHEN ? IS NOT NULL AND ? < CURRENT_DATE THEN 'VENCIDO'
                                      ELSE 'PENDIENTE'
                                    END,
                   updated_at     = NOW()
             WHERE id = ?
        """;

        jdbcClient.sql(sql)
                .params(
                        totalAmount,
                        totalAmount,
                        dueDate,
                        totalAmount,
                        dueDate,
                        dueDate,
                        arId
                )
                .update();
    }

    @Override
    public void recalculateCustomerAccount(Long customerId) {

        // Recalcula en base a accounts_receivable (PENDIENTE/VENCIDO)
        String sql = """
            WITH sums AS (
              SELECT
                COALESCE(SUM(balance_amount), 0) AS current_debt,
                COALESCE(SUM(CASE WHEN status = 'VENCIDO' THEN balance_amount ELSE 0 END), 0) AS overdue_debt
              FROM accounts_receivable
              WHERE customer_id = ?
                AND status IN ('PENDIENTE','VENCIDO')
            )
            INSERT INTO customer_account(customer_id, credit_enabled, credit_limit, current_debt, overdue_debt, status, updated_at)
            SELECT
              ?,
              TRUE,
              0,
              s.current_debt,
              s.overdue_debt,
              CASE WHEN s.overdue_debt > 0 THEN 'MOROSO' ELSE 'OK' END,
              NOW()
            FROM sums s
            ON CONFLICT (customer_id) DO UPDATE
            SET current_debt = EXCLUDED.current_debt,
                overdue_debt = EXCLUDED.overdue_debt,
                status = EXCLUDED.status,
                updated_at = NOW()
        """;

        jdbcClient.sql(sql)
                .params(customerId, customerId)
                .update();
    }
}

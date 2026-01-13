package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.AccountsReceivableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class PostgresAccountsReceivableRepository implements AccountsReceivableRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Long insert(Long saleId, Long customerId, LocalDate issueDate, LocalDate dueDate,
                       BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal balanceAmount, String status) {

        String sql = """
            INSERT INTO accounts_receivable(
              sale_id, customer_id,
              issue_date, due_date,
              total_amount, paid_amount, balance_amount,
              status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        """;

        return jdbcClient.sql(sql)
                .params(saleId, customerId, issueDate, dueDate, totalAmount, paidAmount, balanceAmount, status)
                .query(Long.class)
                .single();
    }

    @Override
    public LockedAr lockById(Long arId) {
        String sql = """
            SELECT
              id,
              customer_id,
              issue_date,
              due_date,
              total_amount,
              paid_amount,
              balance_amount,
              status
            FROM accounts_receivable
            WHERE id = ?
            FOR UPDATE
        """;

        return jdbcClient.sql(sql)
                .param(arId)
                .query((rs, rowNum) -> LockedAr.builder()
                        .id(rs.getLong("id"))
                        .customerId(rs.getLong("customer_id"))
                        .issueDate(rs.getObject("issue_date", LocalDate.class))
                        .dueDate(rs.getObject("due_date", LocalDate.class))
                        .totalAmount(rs.getBigDecimal("total_amount"))
                        .paidAmount(rs.getBigDecimal("paid_amount"))
                        .balanceAmount(rs.getBigDecimal("balance_amount"))
                        .status(rs.getString("status"))
                        .build())
                .optional()
                .orElse(null);
    }

    @Override
    public void updateAmountsAndStatus(Long arId, BigDecimal paidAmount, BigDecimal balanceAmount, String status) {
        String sql = """
            UPDATE accounts_receivable
               SET paid_amount = ?,
                   balance_amount = ?,
                   status = ?,
                   updated_at = NOW()
             WHERE id = ?
        """;

        jdbcClient.sql(sql)
                .params(paidAmount, balanceAmount, status, arId)
                .update();
    }

    @Override
    public void markOverdueForCustomer(Long customerId) {
        String sql = """
            UPDATE accounts_receivable
               SET status = 'VENCIDO',
                   updated_at = NOW()
             WHERE customer_id = ?
               AND status <> 'PAGADO'
               AND balance_amount > 0
               AND due_date < CURRENT_DATE
        """;

        jdbcClient.sql(sql)
                .param(customerId)
                .update();
    }
}

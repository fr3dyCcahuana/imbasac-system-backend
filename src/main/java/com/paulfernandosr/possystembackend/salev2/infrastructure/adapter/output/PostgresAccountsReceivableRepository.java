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
    public LockedAr lockBySaleId(Long saleId) {
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
            WHERE sale_id = ?
            FOR UPDATE
        """;

        return jdbcClient.sql(sql)
                .param(saleId)
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
    public void deleteBySaleId(Long saleId) {
        String sql = "DELETE FROM accounts_receivable WHERE sale_id = ?";
        jdbcClient.sql(sql)
                .param(saleId)
                .update();
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
    public void updateBySaleId(Long saleId, Long customerId, LocalDate issueDate, LocalDate dueDate,
                               BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal balanceAmount, String status) {
        String sql = """
            UPDATE accounts_receivable
               SET customer_id = ?,
                   issue_date = ?,
                   due_date = ?,
                   total_amount = ?,
                   paid_amount = ?,
                   balance_amount = ?,
                   status = ?,
                   updated_at = NOW()
             WHERE sale_id = ?
        """;

        jdbcClient.sql(sql)
                .params(customerId, issueDate, dueDate, totalAmount, paidAmount, balanceAmount, status, saleId)
                .update();
    }

    @Override
    public boolean existsOpenOverdueDebtByCustomerExcludingSale(Long customerId, Long excludedSaleId) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                  FROM accounts_receivable
                 WHERE customer_id = ?
                   AND (? IS NULL OR sale_id <> ?)
                   AND status <> 'PAGADO'
                   AND balance_amount > 0
                   AND due_date < CURRENT_DATE
            )
        """;

        return Boolean.TRUE.equals(
                jdbcClient.sql(sql)
                        .params(customerId, excludedSaleId, excludedSaleId)
                        .query(Boolean.class)
                        .single()
        );
    }
}

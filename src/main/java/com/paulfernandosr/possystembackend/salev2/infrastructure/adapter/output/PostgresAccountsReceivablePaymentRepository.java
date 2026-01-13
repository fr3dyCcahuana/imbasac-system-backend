package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.AccountsReceivablePaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class PostgresAccountsReceivablePaymentRepository implements AccountsReceivablePaymentRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insert(Long arId, BigDecimal amount, String method, LocalDateTime paidAt, String note) {
        String sql = """
            INSERT INTO accounts_receivable_payment(
              ar_id,
              amount,
              method,
              paid_at,
              note
            ) VALUES (?, ?, ?, ?, ?)
        """;

        jdbcClient.sql(sql)
                .params(arId, amount, method, paidAt, note)
                .update();
    }
}

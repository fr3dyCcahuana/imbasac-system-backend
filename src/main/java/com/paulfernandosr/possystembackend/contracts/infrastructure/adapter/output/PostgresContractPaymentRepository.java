package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class PostgresContractPaymentRepository implements ContractPaymentRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Long insert(Long contractId,
                       String paymentType,
                       Integer installmentNumber,
                       BigDecimal amount,
                       String method,
                       LocalDateTime paidAt,
                       String note,
                       Long createdBy,
                       String createdByUsername) {
        String sql = """
            INSERT INTO contract_payment(
              contract_id, payment_type, installment_number, amount, method, paid_at,
              note, created_by, created_by_username, created_at
            ) VALUES (?, ?, ?, ?, ?, COALESCE(?, NOW()), ?, ?, ?, NOW())
            RETURNING id
        """;

        return jdbcClient.sql(sql)
                .params(contractId, paymentType, installmentNumber, amount, method, paidAt, note, createdBy, createdByUsername)
                .query(Long.class)
                .single();
    }

    @Override
    public BigDecimal sumPaidByContractId(Long contractId) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0)
              FROM contract_payment
             WHERE contract_id = ?
               AND payment_type IN ('INICIAL','CUOTA','REGULARIZACION')
        """;
        return jdbcClient.sql(sql)
                .param(contractId)
                .query(BigDecimal.class)
                .single();
    }
}

package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractInstallment;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractInstallmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresContractInstallmentRepository implements ContractInstallmentRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void insertBatch(Long contractId, List<ContractInstallment> rows) {
        if (rows == null || rows.isEmpty()) return;

        String sql = """
            INSERT INTO contract_installment(
              contract_id, installment_number, due_date,
              amount, paid_amount, status, created_at
            ) VALUES (
              ?, ?, ?,
              ?, ?, ?, NOW()
            )
        """;

        for (ContractInstallment r : rows) {
            jdbcClient.sql(sql)
                    .params(
                            contractId,
                            r.getInstallmentNumber(),
                            r.getDueDate(),
                            r.getAmount(),
                            r.getPaidAmount(),
                            r.getStatus()
                    )
                    .update();
        }
    }

    @Override
    public List<ContractInstallment> findByContractId(Long contractId) {
        String sql = """
            SELECT id,
                   contract_id AS contractId,
                   installment_number AS installmentNumber,
                   due_date AS dueDate,
                   amount,
                   paid_amount AS paidAmount,
                   paid_at AS paidAt,
                   paid_by_username AS paidByUsername,
                   status
              FROM contract_installment
             WHERE contract_id = ?
             ORDER BY installment_number
        """;

        return jdbcClient.sql(sql)
                .param(contractId)
                .query(ContractInstallment.class)
                .list();
    }

    @Override
    public LocalDate findLastDueDate(Long contractId) {
        String sql = """
            SELECT MAX(due_date)
              FROM contract_installment
             WHERE contract_id = ?
        """;

        return jdbcClient.sql(sql)
                .param(contractId)
                .query(LocalDate.class)
                .optional()
                .orElse(null);
    }

    @Override
    public LockedInstallment lockByContractIdAndNumber(Long contractId, int installmentNumber) {
        String sql = """
            SELECT id,
                   amount,
                   paid_amount AS paidAmount,
                   paid_at AS paidAt,
                   paid_by_username AS paidByUsername,
                   status
              FROM contract_installment
             WHERE contract_id = ?
               AND installment_number = ?
             FOR UPDATE
        """;

        return jdbcClient.sql(sql)
                .params(contractId, installmentNumber)
                .query((rs, rowNum) -> new LockedInstallment(
                        rs.getLong("id"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("paidAmount"),
                        rs.getString("status")
                ))
                .optional()
                .orElse(null);
    }

    @Override
    public void updatePaidAmountAndStatus(Long contractId, int installmentNumber, BigDecimal paidAmount, String status,
                                     java.time.LocalDateTime paidAt, Long paidBy, String paidByUsername) {
        String sql = """
            UPDATE contract_installment
               SET paid_amount = ?,
                   status = ?,
                   paid_at = ?,
                   paid_by = ?,
                   paid_by_username = ?
             WHERE contract_id = ?
               AND installment_number = ?
        """;
        jdbcClient.sql(sql)
                .params(paidAmount, status, paidAt, paidBy, paidByUsername, contractId, installmentNumber)
                .update();
    }
}

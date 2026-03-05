package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractGuarantor;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractGuarantorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractGuarantorRepository implements ContractGuarantorRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void upsert(Long contractId, ContractGuarantor g) {

        String sql = """
            INSERT INTO contract_guarantor(
              contract_id,
              doc_type, doc_number, full_name,
              address, phone,
              occupation, company_name, monthly_income,
              created_at
            ) VALUES (
              ?, ?, ?, ?,
              ?, ?,
              ?, ?, ?,
              NOW()
            )
            ON CONFLICT (contract_id) DO UPDATE SET
              doc_type = EXCLUDED.doc_type,
              doc_number = EXCLUDED.doc_number,
              full_name = EXCLUDED.full_name,
              address = EXCLUDED.address,
              phone = EXCLUDED.phone,
              occupation = EXCLUDED.occupation,
              company_name = EXCLUDED.company_name,
              monthly_income = EXCLUDED.monthly_income
        """;

        jdbcClient.sql(sql)
                .params(
                        contractId,
                        g.getDocType(), g.getDocNumber(), g.getFullName(),
                        g.getAddress(), g.getPhone(),
                        g.getOccupation(), g.getCompanyName(), g.getMonthlyIncome()
                )
                .update();
    }

    @Override
    public ContractGuarantor findByContractId(Long contractId) {
        String sql = """
            SELECT id,
                   contract_id AS contractId,
                   doc_type AS docType,
                   doc_number AS docNumber,
                   full_name AS fullName,
                   address,
                   phone,
                   occupation,
                   company_name AS companyName,
                   monthly_income AS monthlyIncome
              FROM contract_guarantor
             WHERE contract_id = ?
        """;

        return jdbcClient.sql(sql)
                .param(contractId)
                .query(ContractGuarantor.class)
                .optional()
                .orElse(null);
    }
}

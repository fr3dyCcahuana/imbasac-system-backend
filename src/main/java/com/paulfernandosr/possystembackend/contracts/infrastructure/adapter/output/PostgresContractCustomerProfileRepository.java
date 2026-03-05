package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.ContractCustomerProfile;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractCustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractCustomerProfileRepository implements ContractCustomerProfileRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void upsert(Long contractId, ContractCustomerProfile p) {

        String sql = """
            INSERT INTO contract_customer_profile(
              contract_id,
              marital_status, nationality,
              district, province,
              housing_type, rent_amount,
              employer_name, employer_address, employment_time,
              net_income, spouse_income, other_income,
              total_income,
              customer_references,
              created_at
            ) VALUES (
              ?,
              ?, ?,
              ?, ?,
              ?, ?,
              ?, ?, ?,
              ?, ?, ?,
              ?,
              ?,
              NOW()
            )
            ON CONFLICT (contract_id) DO UPDATE SET
              marital_status = EXCLUDED.marital_status,
              nationality = EXCLUDED.nationality,
              district = EXCLUDED.district,
              province = EXCLUDED.province,
              housing_type = EXCLUDED.housing_type,
              rent_amount = EXCLUDED.rent_amount,
              employer_name = EXCLUDED.employer_name,
              employer_address = EXCLUDED.employer_address,
              employment_time = EXCLUDED.employment_time,
              net_income = EXCLUDED.net_income,
              spouse_income = EXCLUDED.spouse_income,
              other_income = EXCLUDED.other_income,
              total_income = EXCLUDED.total_income,
              customer_references = EXCLUDED.customer_references
        """;

        jdbcClient.sql(sql)
                .params(
                        contractId,
                        p.getMaritalStatus(), p.getNationality(),
                        p.getDistrict(), p.getProvince(),
                        p.getHousingType(), p.getRentAmount(),
                        p.getEmployerName(), p.getEmployerAddress(), p.getEmploymentTime(),
                        p.getNetIncome(), p.getSpouseIncome(), p.getOtherIncome(),
                        p.getTotalIncome(),
                        p.getCustomerReferences()
                )
                .update();
    }

    @Override
    public ContractCustomerProfile findByContractId(Long contractId) {
        String sql = """
            SELECT id,
                   contract_id AS contractId,
                   marital_status AS maritalStatus,
                   nationality,
                   district,
                   province,
                   housing_type AS housingType,
                   rent_amount AS rentAmount,
                   employer_name AS employerName,
                   employer_address AS employerAddress,
                   employment_time AS employmentTime,
                   net_income AS netIncome,
                   spouse_income AS spouseIncome,
                   other_income AS otherIncome,
                   total_income AS totalIncome,
                   customer_references AS customerReferences
              FROM contract_customer_profile
             WHERE contract_id = ?
        """;

        return jdbcClient.sql(sql)
                .param(contractId)
                .query(ContractCustomerProfile.class)
                .optional()
                .orElse(null);
    }
}

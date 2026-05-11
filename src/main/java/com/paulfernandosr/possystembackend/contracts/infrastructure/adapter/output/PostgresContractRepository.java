package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.model.Contract;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PriceList;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresContractRepository implements ContractRepository {

    private final JdbcClient jdbcClient;

    private static final RowMapper<Contract> CONTRACT_ROW_MAPPER = (rs, rowNum) -> Contract.builder()
            .id(rs.getLong("id"))
            .stationId((Long) rs.getObject("stationId"))
            .createdBy(rs.getLong("createdBy"))
            .series(rs.getString("series"))
            .number(rs.getLong("number"))
            .issueDate(rs.getObject("issueDate", java.time.LocalDate.class))
            .currency(rs.getString("currency"))
            .exchangeRate(rs.getBigDecimal("exchangeRate"))
            .priceList(rs.getString("priceList") != null ? PriceList.valueOf(rs.getString("priceList")) : null)
            .customerId((Long) rs.getObject("customerId"))
            .customerDocType(rs.getString("customerDocType"))
            .customerDocNumber(rs.getString("customerDocNumber"))
            .customerName(rs.getString("customerName"))
            .customerAddress(rs.getString("customerAddress"))
            .paymentType(rs.getString("paymentType") != null ? PaymentType.valueOf(rs.getString("paymentType")) : null)
            .cashPrice(rs.getBigDecimal("cashPrice"))
            .interestRateMonthly(rs.getBigDecimal("interestRateMonthly"))
            .installments((Integer) rs.getObject("installments"))
            .initialAmount(rs.getBigDecimal("initialAmount"))
            .financedAmount(rs.getBigDecimal("financedAmount"))
            .interestAmount(rs.getBigDecimal("interestAmount"))
            .totalAmount(rs.getBigDecimal("totalAmount"))
            .status(rs.getString("status") != null ? ContractStatus.valueOf(rs.getString("status")) : null)
            .saleId((Long) rs.getObject("saleId"))
            .notes(rs.getString("notes"))
            .build();

    private static final String CONTRACT_SELECT = """
            SELECT id,
                   station_id AS stationId,
                   created_by AS createdBy,
                   series,
                   number,
                   issue_date AS issueDate,
                   currency,
                   exchange_rate AS exchangeRate,
                   price_list AS priceList,
                   customer_id AS customerId,
                   customer_doc_type AS customerDocType,
                   customer_doc_number AS customerDocNumber,
                   customer_name AS customerName,
                   customer_address AS customerAddress,
                   payment_type AS paymentType,
                   cash_price AS cashPrice,
                   interest_rate_monthly AS interestRateMonthly,
                   installments,
                   initial_amount AS initialAmount,
                   financed_amount AS financedAmount,
                   interest_amount AS interestAmount,
                   total_amount AS totalAmount,
                   status,
                   sale_id AS saleId,
                   notes
              FROM contract
            """;

    @Override
    public Long insert(Contract c) {

        String sql = """
            INSERT INTO contract(
              station_id, created_by,
              series, number, issue_date,
              currency, exchange_rate,
              price_list,
              customer_id, customer_doc_type, customer_doc_number, customer_name, customer_address,
              payment_type,
              cash_price, interest_rate_monthly, installments, initial_amount,
              financed_amount, interest_amount, total_amount,
              status, notes
            ) VALUES (
              ?, ?,
              ?, ?, ?,
              ?, ?,
              ?,
              ?, ?, ?, ?, ?,
              ?,
              ?, ?, ?, ?,
              ?, ?, ?,
              ?, ?
            )
            RETURNING id
        """;

        return jdbcClient.sql(sql)
                .params(
                        c.getStationId(), c.getCreatedBy(),
                        c.getSeries(), c.getNumber(), c.getIssueDate(),
                        c.getCurrency(), c.getExchangeRate(),
                        c.getPriceList() != null ? c.getPriceList().name() : null,
                        c.getCustomerId(), c.getCustomerDocType(), c.getCustomerDocNumber(), c.getCustomerName(), c.getCustomerAddress(),
                        c.getPaymentType() != null ? c.getPaymentType().name() : null,
                        c.getCashPrice(), c.getInterestRateMonthly(), c.getInstallments(), c.getInitialAmount(),
                        c.getFinancedAmount(), c.getInterestAmount(), c.getTotalAmount(),
                        c.getStatus() != null ? c.getStatus().name() : null,
                        c.getNotes()
                )
                .query(Long.class)
                .single();
    }

    @Override
    public Contract findById(Long id) {
        return jdbcClient.sql(CONTRACT_SELECT + " WHERE id = ?")
                .param(id)
                .query(CONTRACT_ROW_MAPPER)
                .optional()
                .orElse(null);
    }

    @Override
    public Contract lockById(Long id) {
        return jdbcClient.sql(CONTRACT_SELECT + " WHERE id = ? FOR UPDATE")
                .param(id)
                .query(CONTRACT_ROW_MAPPER)
                .optional()
                .orElse(null);
    }

    @Override
    public void updateEditableFields(Contract c, Long editedBy, String editedByUsername, String editReason) {
        String sql = """
            UPDATE contract
               SET station_id = ?,
                   issue_date = ?,
                   currency = ?,
                   exchange_rate = ?,
                   price_list = ?,
                   customer_id = ?,
                   customer_doc_type = ?,
                   customer_doc_number = ?,
                   customer_name = ?,
                   customer_address = ?,
                   payment_type = ?,
                   cash_price = ?,
                   interest_rate_monthly = ?,
                   installments = ?,
                   initial_amount = ?,
                   financed_amount = ?,
                   interest_amount = ?,
                   total_amount = ?,
                   notes = ?,
                   edit_count = COALESCE(edit_count, 0) + 1,
                   last_edit_reason = ?,
                   last_edited_by = ?,
                   last_edited_by_username = ?,
                   last_edited_at = NOW(),
                   updated_at = NOW()
             WHERE id = ?
               AND status = 'PENDIENTE'
               AND sale_id IS NULL
        """;

        int updated = jdbcClient.sql(sql)
                .params(
                        c.getStationId(),
                        c.getIssueDate(),
                        c.getCurrency(),
                        c.getExchangeRate(),
                        c.getPriceList() != null ? c.getPriceList().name() : null,
                        c.getCustomerId(),
                        c.getCustomerDocType(),
                        c.getCustomerDocNumber(),
                        c.getCustomerName(),
                        c.getCustomerAddress(),
                        c.getPaymentType() != null ? c.getPaymentType().name() : null,
                        c.getCashPrice(),
                        c.getInterestRateMonthly(),
                        c.getInstallments(),
                        c.getInitialAmount(),
                        c.getFinancedAmount(),
                        c.getInterestAmount(),
                        c.getTotalAmount(),
                        c.getNotes(),
                        editReason,
                        editedBy,
                        editedByUsername,
                        c.getId()
                )
                .update();

        if (updated == 0) {
            throw new com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException(
                    "No se pudo actualizar el contrato. Solo se permite editar contratos PENDIENTE y sin venta asociada."
            );
        }
    }

    @Override
    public void updateStatusAndSale(Long contractId, ContractStatus status, Long saleId, String notes) {
        String sql = """
            UPDATE contract
               SET status = ?,
                   sale_id = ?,
                   notes = COALESCE(?, notes),
                   updated_at = NOW()
             WHERE id = ?
        """;

        jdbcClient.sql(sql)
                .params(status.name(), saleId, notes, contractId)
                .update();
    }

    @Override
    public void updateStatus(Long contractId, ContractStatus status, String notes) {
        String sql = """
            UPDATE contract
               SET status = ?,
                   notes = COALESCE(?, notes),
                   updated_at = NOW()
             WHERE id = ?
        """;
        jdbcClient.sql(sql)
                .params(status.name(), notes, contractId)
                .update();
    }
}

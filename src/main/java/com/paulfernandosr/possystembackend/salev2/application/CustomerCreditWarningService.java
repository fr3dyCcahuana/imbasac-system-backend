package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CustomerCreditWarningItemResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CustomerCreditWarningResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerCreditWarningService {

    private final JdbcClient jdbcClient;

    public CustomerCreditWarningResponse findPendingCredits(Long customerId, String customerDocType, String customerDocNumber) {
        String docType = normalize(customerDocType);
        String docNumber = normalize(customerDocNumber);

        if (customerId == null && (docType.isBlank() || docNumber.isBlank() || isGenericCustomer(docType, docNumber))) {
            return CustomerCreditWarningResponse.builder()
                    .hasPendingCredits(false)
                    .totalBalance(BigDecimal.ZERO)
                    .items(List.of())
                    .build();
        }

        List<CustomerCreditWarningItemResponse> items = jdbcClient.sql("""
            WITH credit_items AS (
                SELECT
                    CASE WHEN COALESCE(pf.id, p_ref.id) IS NOT NULL THEN 'PROFORMA' ELSE 'VENTA' END AS source_type,
                    COALESCE(pf.id, p_ref.id, s.id) AS source_id,
                    CASE WHEN COALESCE(pf.id, p_ref.id) IS NOT NULL THEN 'PROFORMA' ELSE s.doc_type END AS doc_type,
                    COALESCE(pf.series, p_ref.series, s.series) AS series,
                    COALESCE(pf.number, p_ref.number, s.number) AS number,
                    ar.issue_date AS issue_date,
                    ar.due_date AS due_date,
                    ar.total_amount AS total_amount,
                    ar.paid_amount AS paid_amount,
                    ar.balance_amount AS balance_amount,
                    ar.status AS status
                  FROM accounts_receivable ar
                  JOIN sale s ON s.id = ar.sale_id
                  LEFT JOIN sale_reference sr ON sr.sale_id = s.id
                  LEFT JOIN proforma pf ON pf.id = sr.proforma_id
                  LEFT JOIN proforma p_ref ON p_ref.id = s.source_proforma_id
                 WHERE ar.status IN ('PENDIENTE','VENCIDO')
                   AND ar.balance_amount > 0
                   AND (
                        (? IS NOT NULL AND ar.customer_id = ?)
                     OR (? <> '' AND ? <> ''
                         AND UPPER(COALESCE(s.customer_doc_type,'')) = UPPER(?)
                         AND COALESCE(s.customer_doc_number,'') = ?)
                   )

                UNION ALL

                SELECT
                    'PROFORMA' AS source_type,
                    p.id AS source_id,
                    'PROFORMA' AS doc_type,
                    p.series AS series,
                    p.number AS number,
                    p.issue_date AS issue_date,
                    p.due_date AS due_date,
                    p.total AS total_amount,
                    0::numeric AS paid_amount,
                    p.total AS balance_amount,
                    CASE
                        WHEN p.due_date IS NOT NULL AND p.due_date < CURRENT_DATE THEN 'VENCIDO'
                        ELSE 'PENDIENTE'
                    END AS status
                  FROM proforma p
                 WHERE p.payment_type = 'CREDITO'
                   AND p.status = 'PENDIENTE'
                   AND COALESCE(p.total, 0) > 0
                   AND (
                        (? IS NOT NULL AND p.customer_id = ?)
                     OR (? <> '' AND ? <> ''
                         AND UPPER(COALESCE(p.customer_doc_type,'')) = UPPER(?)
                         AND COALESCE(p.customer_doc_number,'') = ?)
                   )

                UNION ALL

                SELECT
                    'CONTRATO' AS source_type,
                    c.id AS source_id,
                    'CONTRATO' AS doc_type,
                    c.series AS series,
                    c.number AS number,
                    c.issue_date AS issue_date,
                    MIN(ci.due_date) AS due_date,
                    SUM(ci.amount) AS total_amount,
                    SUM(COALESCE(ci.paid_amount, 0)) AS paid_amount,
                    SUM(ci.amount - COALESCE(ci.paid_amount, 0)) AS balance_amount,
                    CASE
                        WHEN SUM(CASE WHEN ci.due_date < CURRENT_DATE THEN 1 ELSE 0 END) > 0 THEN 'VENCIDO'
                        ELSE 'PENDIENTE'
                    END AS status
                  FROM contract c
                  JOIN contract_installment ci ON ci.contract_id = c.id
                 WHERE c.payment_type = 'CREDITO'
                   AND c.status IN ('PENDIENTE','CREDITO_ACTIVO')
                   AND ci.status <> 'PAGADO'
                   AND (ci.amount - COALESCE(ci.paid_amount, 0)) > 0
                   AND (
                        (? IS NOT NULL AND c.customer_id = ?)
                     OR (? <> '' AND ? <> ''
                         AND UPPER(COALESCE(c.customer_doc_type,'')) = UPPER(?)
                         AND COALESCE(c.customer_doc_number,'') = ?)
                   )
                 GROUP BY c.id, c.series, c.number, c.issue_date
            )
            SELECT *
              FROM credit_items
             ORDER BY due_date NULLS LAST, source_type, series, number
            """)
                .params(
                        customerId, customerId, docType, docNumber, docType, docNumber,
                        customerId, customerId, docType, docNumber, docType, docNumber,
                        customerId, customerId, docType, docNumber, docType, docNumber
                )
                .query(mapper())
                .list();

        items = items.stream()
                .sorted(Comparator.comparing(CustomerCreditWarningItemResponse::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        BigDecimal totalBalance = items.stream()
                .map(CustomerCreditWarningItemResponse::getBalanceAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CustomerCreditWarningResponse.builder()
                .hasPendingCredits(!items.isEmpty())
                .totalBalance(totalBalance)
                .items(items)
                .build();
    }

    private RowMapper<CustomerCreditWarningItemResponse> mapper() {
        return (rs, rowNum) -> {
            String sourceType = rs.getString("source_type");
            String docType = rs.getString("doc_type");
            String series = rs.getString("series");
            Long number = getLong(rs, "number");

            return CustomerCreditWarningItemResponse.builder()
                    .sourceType(sourceType)
                    .sourceId(getLong(rs, "source_id"))
                    .docType(docType)
                    .series(series)
                    .number(number)
                    .label(buildLabel(sourceType, docType, series, number))
                    .issueDate(rs.getObject("issue_date", LocalDate.class))
                    .dueDate(rs.getObject("due_date", LocalDate.class))
                    .totalAmount(rs.getBigDecimal("total_amount"))
                    .paidAmount(rs.getBigDecimal("paid_amount"))
                    .balanceAmount(rs.getBigDecimal("balance_amount"))
                    .status(rs.getString("status"))
                    .build();
        };
    }

    private Long getLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private String buildLabel(String sourceType, String docType, String series, Long number) {
        String prefix = switch (normalize(sourceType).toUpperCase()) {
            case "CONTRATO" -> "Contrato";
            case "PROFORMA" -> "Proforma";
            default -> normalize(docType).isBlank() ? "Venta" : normalize(docType);
        };
        String serieNumero = normalize(series) + "-" + (number == null ? "" : number);
        return serieNumero.isBlank() || "-".equals(serieNumero) ? prefix : prefix + " " + serieNumero;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isGenericCustomer(String docType, String docNumber) {
        String normalizedType = normalize(docType).toUpperCase();
        String normalizedNumber = normalize(docNumber);
        return "GEN".equals(normalizedType) || "0".equals(normalizedNumber);
    }
}

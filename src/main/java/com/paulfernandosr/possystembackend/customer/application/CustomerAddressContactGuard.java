package com.paulfernandosr.possystembackend.customer.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomerAddressContactGuard {
    private final JdbcClient jdbcClient;

    public Optional<String> validateAddressPhoneForOperation(
            Long customerId,
            Long customerAddressId,
            String customerDocType,
            String customerDocNumber,
            String customerAddress
    ) {
        if (isGenericCustomer(customerDocType, customerDocNumber)) {
            return Optional.empty();
        }

        Long normalizedAddressId = customerAddressId != null && customerAddressId > 0 ? customerAddressId : null;
        String normalizedAddress = trimToNull(customerAddress);
        if (normalizedAddressId == null && normalizedAddress == null) {
            return Optional.of("Registra una direccion del cliente antes de continuar");
        }

        Optional<AddressContactSnapshot> address = findAddress(
                customerId,
                normalizedAddressId,
                customerDocType,
                customerDocNumber,
                normalizedAddress
        );
        if (address.isEmpty()) {
            return Optional.of("Registra la direccion del cliente antes de continuar");
        }

        return Optional.empty();
    }

    private Optional<AddressContactSnapshot> findAddress(
            Long customerId,
            Long customerAddressId,
            String customerDocType,
            String customerDocNumber,
            String customerAddress
    ) {
        if (customerId != null && customerAddressId != null) {
            String sql = """
                    SELECT ca.id, ca.phone
                    FROM customer_address ca
                    WHERE ca.customer_id = ?
                      AND ca.id = ?
                      AND ca.enabled = TRUE
                    LIMIT 1
                    """;

            return jdbcClient.sql(sql)
                    .params(customerId, customerAddressId)
                    .query((rs, rowNum) -> new AddressContactSnapshot(rs.getLong("id"), rs.getString("phone")))
                    .optional();
        }

        if (customerId != null) {
            String sql = """
                    SELECT ca.id, ca.phone
                    FROM customer_address ca
                    WHERE ca.customer_id = ?
                      AND ca.enabled = TRUE
                      AND (
                          (? IS NULL AND ca.fiscal = TRUE)
                          OR UPPER(BTRIM(ca.address)) = UPPER(BTRIM(?))
                      )
                    ORDER BY
                      CASE WHEN UPPER(BTRIM(ca.address)) = UPPER(BTRIM(?)) THEN 0 ELSE 1 END,
                      ca.fiscal DESC,
                      ca.position ASC,
                      ca.id ASC
                    LIMIT 1
                    """;

            return jdbcClient.sql(sql)
                    .params(customerId, customerAddress, customerAddress, customerAddress)
                    .query((rs, rowNum) -> new AddressContactSnapshot(rs.getLong("id"), rs.getString("phone")))
                    .optional();
        }

        if (isBlank(customerDocType) || isBlank(customerDocNumber)) {
            return Optional.empty();
        }

        String sql = """
                SELECT ca.id, ca.phone
                FROM customer_address ca
                INNER JOIN customers c ON c.id = ca.customer_id
                WHERE c.document_type = ?
                  AND c.document_number = ?
                  AND ca.enabled = TRUE
                  AND (
                      (? IS NULL AND ca.fiscal = TRUE)
                      OR UPPER(BTRIM(ca.address)) = UPPER(BTRIM(?))
                  )
                ORDER BY
                  CASE WHEN UPPER(BTRIM(ca.address)) = UPPER(BTRIM(?)) THEN 0 ELSE 1 END,
                  ca.fiscal DESC,
                  ca.position ASC,
                  ca.id ASC
                LIMIT 1
                """;

        return jdbcClient.sql(sql)
                .params(customerDocType.trim(), customerDocNumber.trim(), customerAddress, customerAddress, customerAddress)
                .query((rs, rowNum) -> new AddressContactSnapshot(rs.getLong("id"), rs.getString("phone")))
                .optional();
    }

    private boolean isGenericCustomer(String documentType, String documentNumber) {
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        String number = documentNumber == null ? "" : documentNumber.trim();
        return "GEN".equals(type)
                || "0".equals(type)
                || "0".equals(number)
                || "GENERICO".equals(type)
                || "GENÉRICO".equals(type)
                || "GENERAL".equals(type)
                || "OTROS".equals(type)
                || "SIN_DOCUMENTO".equals(type)
                || "SIN DOCUMENTO".equals(type);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record AddressContactSnapshot(Long id, String phone) {
    }
}

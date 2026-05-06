package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.CustomerLocationSnapshot;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.CustomerLocationSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresCustomerLocationSnapshotRepository implements CustomerLocationSnapshotRepository {

    private final JdbcClient jdbcClient;

    private static final RowMapper<CustomerLocationSnapshot> ROW_MAPPER = (rs, rowNum) ->
            CustomerLocationSnapshot.builder()
                    .ubigeo(rs.getString("ubigeo"))
                    .department(rs.getString("department"))
                    .province(rs.getString("province"))
                    .district(rs.getString("district"))
                    .build();

    @Override
    public Optional<CustomerLocationSnapshot> resolveCustomerLocation(Long customerId,
                                                                      String customerDocType,
                                                                      String customerDocNumber,
                                                                      String customerAddress) {
        if (customerId != null) {
            Optional<CustomerLocationSnapshot> byId = findByCustomerId(customerId, customerAddress);
            if (byId.isPresent()) {
                return byId;
            }
        }

        if (!hasText(customerDocType) || !hasText(customerDocNumber)) {
            return Optional.empty();
        }

        return findByDocument(customerDocType, customerDocNumber, customerAddress);
    }

    private Optional<CustomerLocationSnapshot> findByCustomerId(Long customerId, String customerAddress) {
        String sql = """
            SELECT
                COALESCE(ca.ubigeo, c.ubigeo)         AS ubigeo,
                COALESCE(ca.department, c.department) AS department,
                COALESCE(ca.province, c.province)     AS province,
                COALESCE(ca.district, c.district)     AS district
              FROM customers c
              LEFT JOIN LATERAL (
                  SELECT
                      ca.ubigeo,
                      ca.department,
                      ca.province,
                      ca.district
                    FROM customer_address ca
                   WHERE ca.customer_id = c.id
                     AND ca.enabled = TRUE
                   ORDER BY
                     CASE
                       WHEN ? <> ''
                        AND UPPER(BTRIM(ca.address)) = UPPER(BTRIM(?))
                       THEN 0 ELSE 1
                     END,
                     CASE WHEN ca.fiscal = TRUE THEN 0 ELSE 1 END,
                     ca.position,
                     ca.id
                   LIMIT 1
              ) ca ON TRUE
             WHERE c.id = ?
             LIMIT 1
            """;

        String normalizedAddress = normalize(customerAddress);

        return jdbcClient.sql(sql)
                .params(normalizedAddress, normalizedAddress, customerId)
                .query(ROW_MAPPER)
                .optional();
    }

    private Optional<CustomerLocationSnapshot> findByDocument(String customerDocType,
                                                              String customerDocNumber,
                                                              String customerAddress) {
        String sql = """
            SELECT
                COALESCE(ca.ubigeo, c.ubigeo)         AS ubigeo,
                COALESCE(ca.department, c.department) AS department,
                COALESCE(ca.province, c.province)     AS province,
                COALESCE(ca.district, c.district)     AS district
              FROM customers c
              LEFT JOIN LATERAL (
                  SELECT
                      ca.ubigeo,
                      ca.department,
                      ca.province,
                      ca.district
                    FROM customer_address ca
                   WHERE ca.customer_id = c.id
                     AND ca.enabled = TRUE
                   ORDER BY
                     CASE
                       WHEN ? <> ''
                        AND UPPER(BTRIM(ca.address)) = UPPER(BTRIM(?))
                       THEN 0 ELSE 1
                     END,
                     CASE WHEN ca.fiscal = TRUE THEN 0 ELSE 1 END,
                     ca.position,
                     ca.id
                   LIMIT 1
              ) ca ON TRUE
             WHERE c.document_type = ?
               AND c.document_number = ?
             ORDER BY c.id DESC
             LIMIT 1
            """;

        String normalizedAddress = normalize(customerAddress);

        return jdbcClient.sql(sql)
                .params(normalizedAddress, normalizedAddress, customerDocType, customerDocNumber)
                .query(ROW_MAPPER)
                .optional();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

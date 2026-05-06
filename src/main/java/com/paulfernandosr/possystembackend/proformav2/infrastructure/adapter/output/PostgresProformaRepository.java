package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.proformav2.domain.CustomerLocationSnapshot;
import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper.ProformaRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresProformaRepository implements ProformaRepository {

    private final JdbcClient jdbcClient;

    private static final RowMapper<CustomerLocationSnapshot> CUSTOMER_LOCATION_ROW_MAPPER = (rs, rowNum) ->
            CustomerLocationSnapshot.builder()
                    .ubigeo(rs.getString("ubigeo"))
                    .department(rs.getString("department"))
                    .province(rs.getString("province"))
                    .district(rs.getString("district"))
                    .build();

    private static final String CUSTOMER_LOCATION_LATERAL_SQL = """
            LEFT JOIN LATERAL (
                SELECT
                    COALESCE(ca.ubigeo, cu.ubigeo)         AS ubigeo,
                    COALESCE(ca.department, cu.department) AS department,
                    COALESCE(ca.province, cu.province)     AS province,
                    COALESCE(ca.district, cu.district)     AS district
                  FROM customers cu
                  LEFT JOIN LATERAL (
                      SELECT
                          ca.ubigeo,
                          ca.department,
                          ca.province,
                          ca.district
                        FROM customer_address ca
                       WHERE ca.customer_id = cu.id
                         AND ca.enabled = TRUE
                       ORDER BY
                         CASE
                           WHEN p.customer_address IS NOT NULL
                            AND BTRIM(p.customer_address) <> ''
                            AND UPPER(BTRIM(ca.address)) = UPPER(BTRIM(p.customer_address))
                           THEN 0 ELSE 1
                         END,
                         CASE WHEN ca.fiscal = TRUE THEN 0 ELSE 1 END,
                         ca.position,
                         ca.id
                       LIMIT 1
                  ) ca ON TRUE
                 WHERE (p.customer_id IS NOT NULL AND cu.id = p.customer_id)
                    OR (
                        p.customer_id IS NULL
                        AND p.customer_doc_type IS NOT NULL
                        AND p.customer_doc_number IS NOT NULL
                        AND cu.document_type = p.customer_doc_type
                        AND cu.document_number = p.customer_doc_number
                    )
                 ORDER BY CASE WHEN p.customer_id IS NOT NULL AND cu.id = p.customer_id THEN 0 ELSE 1 END
                 LIMIT 1
            ) c ON TRUE
            """;

    private static final String SELECT_PROFORMA_WITH_RESOLVED_CUSTOMER_LOCATION = """
            SELECT
              p.*,
              u.username   AS cashier_username,
              u.first_name AS cashier_first_name,
              u.last_name  AS cashier_last_name,
              COALESCE(p.customer_ubigeo, c.ubigeo)         AS customer_ubigeo_resolved,
              COALESCE(p.customer_department, c.department) AS customer_department_resolved,
              COALESCE(p.customer_province, c.province)     AS customer_province_resolved,
              COALESCE(p.customer_district, c.district)     AS customer_district_resolved
            FROM proforma p
            LEFT JOIN users u ON u.id = p.created_by
            """ + CUSTOMER_LOCATION_LATERAL_SQL;

    @Override
    public Proforma create(Proforma proforma) {

        String sql = """
        INSERT INTO proforma(
          station_id, created_by,
          series, number, issue_date,
          price_list, currency,

          tax_status, igv_rate, igv_included, igv_amount,

          customer_id, customer_doc_type, customer_doc_number, customer_name, customer_address,
          customer_ubigeo, customer_department, customer_province, customer_district,
          payment_type, credit_days, due_date,
          notes,

          subtotal, discount_total, total,
          status,

          created_at, updated_at
        ) VALUES (
          ?, ?,
          ?, ?, ?,
          ?, ?,

          ?, ?, ?, ?,

          ?, ?, ?, ?, ?,
          ?, ?, ?, ?,
          ?, ?, ?,
          ?,

          ?, ?, ?,
          ?,

          NOW(), NOW()
        )
        RETURNING *
        """;

        return jdbcClient.sql(sql)
                .params(
                        proforma.getStationId(),
                        proforma.getCreatedBy(),

                        proforma.getSeries(),
                        proforma.getNumber(),
                        java.sql.Date.valueOf(proforma.getIssueDate()),

                        String.valueOf(proforma.getPriceList()),
                        proforma.getCurrency(),

                        proforma.getTaxStatus(),
                        proforma.getIgvRate(),
                        (proforma.getIgvIncluded() != null ? proforma.getIgvIncluded() : Boolean.FALSE),
                        proforma.getIgvAmount(),

                        proforma.getCustomerId(),
                        proforma.getCustomerDocType(),
                        proforma.getCustomerDocNumber(),
                        proforma.getCustomerName(),
                        proforma.getCustomerAddress(),
                        proforma.getCustomerUbigeo(),
                        proforma.getCustomerDepartment(),
                        proforma.getCustomerProvince(),
                        proforma.getCustomerDistrict(),

                        proforma.getPaymentType() != null ? proforma.getPaymentType().name() : null,
                        proforma.getCreditDays(),
                        proforma.getDueDate() != null ? java.sql.Date.valueOf(proforma.getDueDate()) : null,

                        proforma.getNotes(),

                        proforma.getSubtotal(),
                        proforma.getDiscountTotal(),
                        proforma.getTotal(),

                        proforma.getStatus().name()
                )
                .query(new ProformaRowMapper())
                .single();
    }

    @Override
    public Optional<CustomerLocationSnapshot> resolveCustomerLocation(
            Long customerId,
            String customerDocType,
            String customerDocNumber,
            String customerAddress
    ) {
        if (customerId != null) {
            Optional<CustomerLocationSnapshot> byId = findCustomerLocationById(customerId, customerAddress);
            if (byId.isPresent()) {
                return byId;
            }
        }

        if (!hasText(customerDocType) || !hasText(customerDocNumber)) {
            return Optional.empty();
        }

        return findCustomerLocationByDocument(customerDocType, customerDocNumber, customerAddress);
    }

    private Optional<CustomerLocationSnapshot> findCustomerLocationById(Long customerId, String customerAddress) {
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
                .query(CUSTOMER_LOCATION_ROW_MAPPER)
                .optional();
    }

    private Optional<CustomerLocationSnapshot> findCustomerLocationByDocument(
            String customerDocType,
            String customerDocNumber,
            String customerAddress
    ) {
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
                .query(CUSTOMER_LOCATION_ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Proforma> lockById(Long proformaId) {
        String sql = SELECT_PROFORMA_WITH_RESOLVED_CUSTOMER_LOCATION + """
            WHERE p.id = ?
            FOR UPDATE OF p
            """;

        return jdbcClient.sql(sql)
                .param(proformaId)
                .query(new ProformaRowMapper())
                .optional();
    }

    @Override
    public Optional<Proforma> lockByNumber(Long number) {
        String sql = SELECT_PROFORMA_WITH_RESOLVED_CUSTOMER_LOCATION + """
            WHERE p.number = ?
            ORDER BY p.id DESC
            LIMIT 1
            FOR UPDATE OF p
            """;

        return jdbcClient.sql(sql)
                .param(number)
                .query(new ProformaRowMapper())
                .optional();
    }

    @Override
    public Optional<Proforma> findById(Long proformaId) {
        String sql = SELECT_PROFORMA_WITH_RESOLVED_CUSTOMER_LOCATION + """
            WHERE p.id = ?
            """;

        return jdbcClient.sql(sql)
                .param(proformaId)
                .query(new ProformaRowMapper())
                .optional();
    }

    @Override
    public Optional<Proforma> findByNumber(Long number) {
        String sql = SELECT_PROFORMA_WITH_RESOLVED_CUSTOMER_LOCATION + """
            WHERE p.number = ?
            ORDER BY p.id DESC
            LIMIT 1
            """;

        return jdbcClient.sql(sql)
                .param(number)
                .query(new ProformaRowMapper())
                .optional();
    }

    @Override
    public void updateStatus(Long proformaId, String status) {
        String sql = """
            UPDATE proforma
            SET status = ?, updated_at = NOW()
            WHERE id = ?
            """;
        jdbcClient.sql(sql).params(status, proformaId).update();
    }

    @Override
    public void appendNotesAndSetStatus(Long proformaId, String noteToAppend, String status) {
        String sql = """
            UPDATE proforma
               SET status = ?,
                   notes = CASE
                             WHEN ? IS NULL OR ? = '' THEN notes
                             WHEN notes IS NULL OR notes = '' THEN ?
                             ELSE notes || E'\n' || ?
                           END,
                   updated_at = NOW()
             WHERE id = ?
        """;

        jdbcClient.sql(sql)
                .params(status, noteToAppend, noteToAppend, noteToAppend, noteToAppend, proformaId)
                .update();
    }

    @Override
    public void updateEditable(Proforma proforma) {
        String sql = """
            UPDATE proforma
               SET issue_date = ?,
                   price_list = ?,
                   currency = ?,
                   tax_status = ?,
                   igv_rate = ?,
                   igv_included = ?,
                   igv_amount = ?,
                   customer_id = ?,
                   customer_doc_type = ?,
                   customer_doc_number = ?,
                   customer_name = ?,
                   customer_address = ?,
                   customer_ubigeo = ?,
                   customer_department = ?,
                   customer_province = ?,
                   customer_district = ?,
                   payment_type = ?,
                   credit_days = ?,
                   due_date = ?,
                   notes = ?,
                   subtotal = ?,
                   discount_total = ?,
                   total = ?,
                   updated_at = NOW()
             WHERE id = ?
            """;

        jdbcClient.sql(sql)
                .params(
                        java.sql.Date.valueOf(proforma.getIssueDate()),
                        String.valueOf(proforma.getPriceList()),
                        proforma.getCurrency(),
                        proforma.getTaxStatus(),
                        proforma.getIgvRate(),
                        proforma.getIgvIncluded() != null ? proforma.getIgvIncluded() : Boolean.FALSE,
                        proforma.getIgvAmount(),

                        proforma.getCustomerId(),
                        proforma.getCustomerDocType(),
                        proforma.getCustomerDocNumber(),
                        proforma.getCustomerName(),
                        proforma.getCustomerAddress(),
                        proforma.getCustomerUbigeo(),
                        proforma.getCustomerDepartment(),
                        proforma.getCustomerProvince(),
                        proforma.getCustomerDistrict(),

                        proforma.getPaymentType() != null ? proforma.getPaymentType().name() : null,
                        proforma.getCreditDays(),
                        proforma.getDueDate() != null ? java.sql.Date.valueOf(proforma.getDueDate()) : null,
                        proforma.getNotes(),

                        proforma.getSubtotal(),
                        proforma.getDiscountTotal(),
                        proforma.getTotal(),

                        proforma.getId()
                )
                .update();
    }

    @Override
    public void touchUpdatedAt(Long proformaId) {
        String sql = """
            UPDATE proforma
            SET updated_at = NOW()
            WHERE id = ?
            """;
        jdbcClient.sql(sql).param(proformaId).update();
    }

    @Override
    public int markAsConverted(Long proformaId, Long saleId, Long convertedBy) {
        String sql = """
        UPDATE proforma
           SET status = ?,
               converted_sale_id = ?,
               converted_at = NOW(),
               converted_by = ?,
               updated_at = NOW()
         WHERE id = ?
           AND status = ?
        """;

        return jdbcClient.sql(sql)
                .params(
                        ProformaStatus.CONVERTIDA.name(),
                        saleId,
                        convertedBy,
                        proformaId,
                        ProformaStatus.PENDIENTE.name()
                )
                .update();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

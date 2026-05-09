package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.mapper.ProformaItemRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresProformaItemRepository implements ProformaItemRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void batchCreate(List<ProformaItem> items) {
        if (items == null || items.isEmpty()) return;

        String sql = """
            INSERT INTO proforma_item(
              proforma_id, line_number,
              product_id, sku, description, presentation, factor,
              quantity, unit_price,
              discount_percent, discount_amount, line_subtotal,
              facturable_sunat, affects_stock,
              created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?, NOW())
            """;

        for (ProformaItem it : items) {
            String descriptionToPersist = normalizeDescriptionForInsert(it.getDescription(), it.getSku());

            System.out.println("[PROFORMA][DB_INSERT_ITEM] proformaId=" + it.getProformaId()
                    + ", line=" + it.getLineNumber()
                    + ", productId=" + it.getProductId()
                    + ", sku=" + it.getSku()
                    + ", facturableSunat=" + it.getFacturableSunat()
                    + ", affectsStock=" + it.getAffectsStock()
                    + ", description=" + descriptionToPersist);

            jdbcClient.sql(sql)
                    .params(
                            it.getProformaId(),
                            it.getLineNumber(),
                            it.getProductId(),
                            it.getSku(),
                            descriptionToPersist,
                            it.getPresentation(),
                            it.getFactor(),
                            it.getQuantity(),
                            it.getUnitPrice(),
                            it.getDiscountPercent(),
                            it.getDiscountAmount(),
                            it.getLineSubtotal(),
                            it.getFacturableSunat(),
                            it.getAffectsStock()
                    )
                    .update();
        }
    }

    @Override
    public List<ProformaItem> findByProformaId(Long proformaId) {
        String sql = """
          SELECT
            pi.*,
            p.warehouse_location AS warehouse_location,
            CASE
              WHEN p.manage_by_serial = TRUE THEN COALESCE(su_agg.serial_qty, 0)
              ELSE COALESCE(ps.quantity_on_hand, 0)
            END AS stock_available
          FROM proforma_item pi
          JOIN product p
            ON p.id = pi.product_id
          LEFT JOIN product_stock ps
            ON ps.product_id = p.id
          LEFT JOIN (
            SELECT
              product_id,
              COUNT(*)::numeric(14,3) AS serial_qty
            FROM product_serial_unit
            WHERE status = 'EN_ALMACEN'
            GROUP BY product_id
          ) su_agg
            ON su_agg.product_id = p.id
          WHERE pi.proforma_id = ?
          ORDER BY pi.line_number ASC
          """;
        return jdbcClient.sql(sql)
                .param(proformaId)
                .query(new ProformaItemRowMapper())
                .list();
    }
    @Override
    public void deleteByProformaId(Long proformaId) {
        String sql = """
            DELETE FROM proforma_item
            WHERE proforma_id = ?
            """;
        jdbcClient.sql(sql)
                .param(proformaId)
                .update();
    }

    private String normalizeDescriptionForInsert(String value, String sku) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            normalized = sku == null || sku.isBlank() ? "ITEM" : sku.trim();
        }
        return normalized.length() > 250 ? normalized.substring(0, 250) : normalized;
    }

}

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
            jdbcClient.sql(sql)
                    .params(
                            it.getProformaId(),
                            it.getLineNumber(),
                            it.getProductId(),
                            it.getSku(),
                            it.getDescription(),
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
            SELECT *
            FROM proforma_item
            WHERE proforma_id = ?
            ORDER BY line_number ASC
            """;
        return jdbcClient.sql(sql)
                .param(proformaId)
                .query(new ProformaItemRowMapper())
                .list();
    }
}

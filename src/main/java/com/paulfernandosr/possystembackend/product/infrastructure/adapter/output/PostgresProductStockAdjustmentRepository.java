package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductStockAdjustment;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockAdjustmentRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductStockAdjustmentRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresProductStockAdjustmentRepository implements ProductStockAdjustmentRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ProductStockAdjustment create(ProductStockAdjustment adjustment) {
        String sql = """
            INSERT INTO product_stock_adjustment(
              product_id, movement_type,
              quantity, unit_cost, total_cost,
              reason, note, created_by
            ) VALUES (?,?,?,?,?,?,?,?)
            RETURNING id, product_id, movement_type, quantity, unit_cost, total_cost, reason, note, created_by, created_at
            """;

        return jdbcClient.sql(sql)
                .params(
                        adjustment.getProductId(),
                        adjustment.getMovementType(),
                        adjustment.getQuantity(),
                        adjustment.getUnitCost(),
                        adjustment.getTotalCost(),
                        adjustment.getReason(),
                        adjustment.getNote(),
                        adjustment.getCreatedBy()
                )
                .query(new ProductStockAdjustmentRowMapper())
                .single();
    }
}

package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductStock;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductStockRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("productPostgresProductStockRepository")
@RequiredArgsConstructor
public class PostgresProductStockRepository implements ProductStockRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<ProductStock> findByProductIdForUpdate(Long productId) {
        String sql = """
            SELECT product_id, quantity_on_hand, average_cost, last_unit_cost, last_movement_at
              FROM product_stock
             WHERE product_id = ?
             FOR UPDATE
            """;

        return jdbcClient.sql(sql)
                .params(productId)
                .query(new ProductStockRowMapper())
                .optional();
    }

    @Override
    public void upsert(ProductStock stock) {
        String sql = """
            INSERT INTO product_stock(
              product_id, quantity_on_hand, average_cost, last_unit_cost, last_movement_at
            ) VALUES (?,?,?,?, NOW())
            ON CONFLICT (product_id) DO UPDATE SET
              quantity_on_hand = EXCLUDED.quantity_on_hand,
              average_cost     = EXCLUDED.average_cost,
              last_unit_cost   = EXCLUDED.last_unit_cost,
              last_movement_at = EXCLUDED.last_movement_at
            """;

        jdbcClient.sql(sql)
                .params(
                        stock.getProductId(),
                        stock.getQuantityOnHand(),
                        stock.getAverageCost(),
                        stock.getLastUnitCost()
                )
                .update();
    }
}

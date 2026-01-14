package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class PostgresProductStockRepository implements ProductStockRepository {

    private final JdbcClient jdbcClient;

    @Override
    public BigDecimal getOnHand(Long productId) {
        String sql = "SELECT quantity_on_hand FROM product_stock WHERE product_id = ?";
        return jdbcClient.sql(sql)
                .param(productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getAverageCost(Long productId) {
        String sql = "SELECT average_cost FROM product_stock WHERE product_id = ?";
        return jdbcClient.sql(sql)
                .param(productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getLastUnitCost(Long productId) {
        String sql = "SELECT last_unit_cost FROM product_stock WHERE product_id = ?";
        return jdbcClient.sql(sql)
                .param(productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public void decreaseOnHandOrFail(Long productId, BigDecimal quantity) {
        String sql = """
            UPDATE product_stock
               SET quantity_on_hand = quantity_on_hand - ?
             WHERE product_id = ?
               AND quantity_on_hand >= ?
        """;

        int updated = jdbcClient.sql(sql)
                .params(quantity, productId, quantity)
                .update();

        if (updated == 0) {
            throw new InvalidSaleV2Exception("Stock insuficiente para productId=" + productId);
        }
    }

    @Override
    public void increaseOnHand(Long productId, BigDecimal quantity) {
        // UPSERT para cubrir el caso en que aún no exista fila en product_stock.
        String sql = """
            INSERT INTO product_stock(product_id, quantity_on_hand, last_movement_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (product_id)
            DO UPDATE SET quantity_on_hand = product_stock.quantity_on_hand + EXCLUDED.quantity_on_hand,
                          last_movement_at = NOW()
        """;

        jdbcClient.sql(sql)
                .params(productId, quantity)
                .update();
    }
}

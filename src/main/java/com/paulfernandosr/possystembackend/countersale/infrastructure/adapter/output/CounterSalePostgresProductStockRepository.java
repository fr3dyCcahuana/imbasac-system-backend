package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresProductStockRepository implements ProductStockRepository {

    private final JdbcClient jdbcClient;

    @Override
    public BigDecimal getOnHand(Long productId) {
        return jdbcClient.sql("SELECT quantity_on_hand FROM product_stock WHERE product_id = ?")
                .param(productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getAverageCost(Long productId) {
        return jdbcClient.sql("SELECT average_cost FROM product_stock WHERE product_id = ?")
                .param(productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getLastUnitCost(Long productId) {
        return jdbcClient.sql("SELECT last_unit_cost FROM product_stock WHERE product_id = ?")
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
        int updated = jdbcClient.sql(sql).params(quantity, productId, quantity).update();
        if (updated == 0) {
            throw new InvalidCounterSaleException("Stock insuficiente para productId=" + productId);
        }
    }

    @Override
    public void increaseOnHand(Long productId, BigDecimal quantity) {
        String sql = """
            INSERT INTO product_stock(product_id, quantity_on_hand, last_movement_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (product_id)
            DO UPDATE SET quantity_on_hand = product_stock.quantity_on_hand + EXCLUDED.quantity_on_hand,
                          last_movement_at = NOW()
        """;
        jdbcClient.sql(sql).params(productId, quantity).update();
    }
}

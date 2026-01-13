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
    public void decreaseOnHand(Long productId, BigDecimal quantity) {
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
            BigDecimal onHand = getOnHand(productId);
            throw new InvalidSaleV2Exception(
                    "Stock insuficiente para productId=" + productId + ". Disponible=" + onHand + ", requerido=" + quantity
            );
        }
    }

    @Override
    public BigDecimal getOnHand(Long productId) {
        String sql = """
            SELECT COALESCE(quantity_on_hand, 0)
              FROM product_stock
             WHERE product_id = ?
            """;
        return jdbcClient.sql(sql)
                .param(productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }
}

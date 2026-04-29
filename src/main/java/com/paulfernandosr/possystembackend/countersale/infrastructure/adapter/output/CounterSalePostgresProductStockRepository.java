package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.exception.InvalidCounterSaleException;
import com.paulfernandosr.possystembackend.countersale.domain.model.StockMovementBalance;
import com.paulfernandosr.possystembackend.countersale.domain.port.output.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresProductStockRepository implements ProductStockRepository {

    private final JdbcClient jdbcClient;

    private static final RowMapper<StockMovementBalance> STOCK_BALANCE_ROW_MAPPER = (rs, rowNum) -> StockMovementBalance.builder()
            .quantityOnHand(rs.getBigDecimal("quantity_on_hand"))
            .averageCost(rs.getBigDecimal("average_cost"))
            .lastUnitCost(rs.getBigDecimal("last_unit_cost"))
            .build();

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
    public StockMovementBalance decreaseOnHandOrFail(Long productId, BigDecimal quantity) {
        String sql = """
            UPDATE product_stock
               SET quantity_on_hand = quantity_on_hand - ?,
                   last_movement_at = NOW()
             WHERE product_id = ?
               AND quantity_on_hand >= ?
            RETURNING quantity_on_hand, average_cost, last_unit_cost
        """;

        return jdbcClient.sql(sql)
                .params(quantity, productId, quantity)
                .query(STOCK_BALANCE_ROW_MAPPER)
                .optional()
                .orElseThrow(() -> new InvalidCounterSaleException("Stock insuficiente para productId=" + productId));
    }

    @Override
    public StockMovementBalance increaseOnHand(Long productId, BigDecimal quantity) {
        String sql = """
            INSERT INTO product_stock(product_id, quantity_on_hand, last_movement_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (product_id)
            DO UPDATE SET quantity_on_hand = product_stock.quantity_on_hand + EXCLUDED.quantity_on_hand,
                          last_movement_at = NOW()
            RETURNING quantity_on_hand, average_cost, last_unit_cost
        """;

        return jdbcClient.sql(sql)
                .params(productId, quantity)
                .query(STOCK_BALANCE_ROW_MAPPER)
                .single();
    }
}

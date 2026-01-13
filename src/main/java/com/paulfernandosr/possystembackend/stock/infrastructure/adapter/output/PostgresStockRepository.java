package com.paulfernandosr.possystembackend.stock.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.stock.domain.Stock;
import com.paulfernandosr.possystembackend.stock.domain.StockMovement;
import com.paulfernandosr.possystembackend.stock.domain.port.output.StockRepository;
import com.paulfernandosr.possystembackend.stock.infrastructure.adapter.output.mapper.StockRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class PostgresStockRepository implements StockRepository {

    private final JdbcClient jdbcClient;

    public PostgresStockRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Stock> findByProductIdForUpdate(Long productId) {
        String sql = """
                SELECT
                    product_id,
                    quantity_on_hand,
                    average_cost,
                    last_unit_cost,
                    last_movement_at
                FROM product_stock
                WHERE product_id = ?
                FOR UPDATE
                """;

        return jdbcClient.sql(sql)
                .param(productId)
                .query(new StockRowMapper())
                .optional();
    }

    @Override
    public Stock saveOrUpdateStock(Stock stock) {
        String sql = """
                INSERT INTO product_stock(
                    product_id,
                    quantity_on_hand,
                    average_cost,
                    last_unit_cost,
                    last_movement_at
                )
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (product_id) DO UPDATE
                    SET quantity_on_hand = EXCLUDED.quantity_on_hand,
                        average_cost     = EXCLUDED.average_cost,
                        last_unit_cost   = EXCLUDED.last_unit_cost,
                        last_movement_at = EXCLUDED.last_movement_at
                RETURNING
                    product_id,
                    quantity_on_hand,
                    average_cost,
                    last_unit_cost,
                    last_movement_at
                """;

        LocalDateTime lastMovementAt = stock.getLastMovementAt() != null
                ? stock.getLastMovementAt()
                : LocalDateTime.now();

        return jdbcClient.sql(sql)
                .params(
                        stock.getProductId(),
                        stock.getQuantityOnHand(),
                        stock.getAverageCost(),
                        stock.getLastUnitCost(),
                        Timestamp.valueOf(lastMovementAt)
                )
                .query(new StockRowMapper())
                .single();
    }

    @Override
    public StockMovement createMovement(StockMovement movement) {
        String sql = """
                INSERT INTO product_stock_movement(
                    product_id,
                    movement_type,
                    source_table,
                    source_id,
                    quantity_in,
                    quantity_out,
                    unit_cost,
                    total_cost,
                    balance_qty,
                    balance_cost,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        LocalDateTime createdAt = movement.getCreatedAt() != null
                ? movement.getCreatedAt()
                : LocalDateTime.now();

        Long id = jdbcClient.sql(sql)
                .params(
                        movement.getProductId(),
                        movement.getMovementType(),
                        movement.getSourceTable(),
                        movement.getSourceId(),
                        movement.getQuantityIn(),
                        movement.getQuantityOut(),
                        movement.getUnitCost(),
                        movement.getTotalCost(),
                        movement.getBalanceQty(),
                        movement.getBalanceCost(),
                        Timestamp.valueOf(createdAt)
                )
                .query(Long.class)
                .single();

        movement.setId(id);
        movement.setCreatedAt(createdAt);
        return movement;
    }
}

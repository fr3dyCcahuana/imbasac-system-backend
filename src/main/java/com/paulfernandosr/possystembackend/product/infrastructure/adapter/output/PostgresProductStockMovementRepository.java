package com.paulfernandosr.possystembackend.product.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.product.domain.ProductStockMovement;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockMovementRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.output.mapper.ProductStockMovementRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository("productPostgresProductStockMovementRepository")
@RequiredArgsConstructor
public class PostgresProductStockMovementRepository implements ProductStockMovementRepository {

    private final JdbcClient jdbcClient;

    @Override
    public ProductStockMovement create(ProductStockMovement movement) {
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
              balance_cost
            ) VALUES (?,?,?,?,?,?,?,?,?,?)
            RETURNING id, product_id, movement_type, source_table, source_id,
                      quantity_in, quantity_out, unit_cost, total_cost,
                      balance_qty, balance_cost, created_at
            """;

        return jdbcClient.sql(sql)
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
                        movement.getBalanceCost()
                )
                .query(new ProductStockMovementRowMapper())
                .single();
    }
}

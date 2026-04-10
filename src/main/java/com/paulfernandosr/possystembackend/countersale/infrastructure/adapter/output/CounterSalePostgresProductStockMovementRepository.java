package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.countersale.domain.port.output.ProductStockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class CounterSalePostgresProductStockMovementRepository implements ProductStockMovementRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void createOutCounterSale(Long productId, BigDecimal quantityOut, Long counterSaleItemId) {
        String sql = """
            INSERT INTO product_stock_movement(
              product_id,
              movement_type,
              source_table,
              source_id,
              quantity_out,
              created_at
            ) VALUES (?, 'OUT_COUNTER_SALE', 'counter_sale_item', ?, ?, NOW())
        """;
        jdbcClient.sql(sql)
                .params(productId, counterSaleItemId, quantityOut)
                .update();
    }

    @Override
    public void createInCounterSaleVoid(Long productId, BigDecimal quantityIn, Long counterSaleItemId, BigDecimal unitCost, BigDecimal totalCost) {
        String sql = """
            INSERT INTO product_stock_movement(
              product_id,
              movement_type,
              source_table,
              source_id,
              quantity_in,
              unit_cost,
              total_cost,
              created_at
            ) VALUES (?, 'IN_COUNTER_SALE_VOID', 'counter_sale_item', ?, ?, ?, ?, NOW())
        """;
        jdbcClient.sql(sql)
                .params(productId, counterSaleItemId, quantityIn, unitCost, totalCost)
                .update();
    }
}

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
    public void createOutCounterSale(Long productId,
                                     BigDecimal quantityOut,
                                     Long counterSaleItemId,
                                     BigDecimal unitCost,
                                     BigDecimal totalCost,
                                     BigDecimal balanceQty,
                                     BigDecimal balanceCost) {
        createMovement(productId, "OUT_COUNTER_SALE", "counter_sale_item", counterSaleItemId,
                BigDecimal.ZERO, quantityOut, unitCost, totalCost, balanceQty, balanceCost);
    }

    @Override
    public void createInCounterSaleVoid(Long productId,
                                        BigDecimal quantityIn,
                                        Long counterSaleItemId,
                                        BigDecimal unitCost,
                                        BigDecimal totalCost,
                                        BigDecimal balanceQty,
                                        BigDecimal balanceCost) {
        createMovement(productId, "IN_COUNTER_SALE_VOID", "counter_sale_item", counterSaleItemId,
                quantityIn, BigDecimal.ZERO, unitCost, totalCost, balanceQty, balanceCost);
    }

    private void createMovement(Long productId,
                                String movementType,
                                String sourceTable,
                                Long sourceId,
                                BigDecimal quantityIn,
                                BigDecimal quantityOut,
                                BigDecimal unitCost,
                                BigDecimal totalCost,
                                BigDecimal balanceQty,
                                BigDecimal balanceCost) {
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """;

        jdbcClient.sql(sql)
                .params(productId, movementType, sourceTable, sourceId,
                        quantityIn, quantityOut, unitCost, totalCost, balanceQty, balanceCost)
                .update();
    }
}

package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository("salev2PostgresProductStockMovementRepository")
@RequiredArgsConstructor
public class PostgresProductStockMovementRepository implements ProductStockMovementRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void createOutSale(Long productId,
                              BigDecimal quantityOut,
                              Long saleItemId,
                              BigDecimal unitCost,
                              BigDecimal totalCost,
                              BigDecimal balanceQty,
                              BigDecimal balanceCost) {
        createMovement(productId, "OUT_SALE", "sale_item", saleItemId,
                BigDecimal.ZERO, quantityOut, unitCost, totalCost, balanceQty, balanceCost);
    }

    @Override
    public void createOutEdit(Long productId,
                              BigDecimal quantityOut,
                              Long saleItemId,
                              BigDecimal unitCost,
                              BigDecimal totalCost,
                              BigDecimal balanceQty,
                              BigDecimal balanceCost) {
        createMovement(productId, "OUT_SALE_EDIT", "sale_item", saleItemId,
                BigDecimal.ZERO, quantityOut, unitCost, totalCost, balanceQty, balanceCost);
    }

    @Override
    public void createInReturn(Long productId,
                               BigDecimal quantityIn,
                               Long saleItemId,
                               BigDecimal unitCost,
                               BigDecimal totalCost,
                               BigDecimal balanceQty,
                               BigDecimal balanceCost) {
        createMovement(productId, "IN_RETURN", "sale_item", saleItemId,
                quantityIn, BigDecimal.ZERO, unitCost, totalCost, balanceQty, balanceCost);
    }

    @Override
    public void createInEdit(Long productId,
                             BigDecimal quantityIn,
                             Long saleItemId,
                             BigDecimal unitCost,
                             BigDecimal totalCost,
                             BigDecimal balanceQty,
                             BigDecimal balanceCost) {
        createMovement(productId, "IN_SALE_EDIT", "sale_item", saleItemId,
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

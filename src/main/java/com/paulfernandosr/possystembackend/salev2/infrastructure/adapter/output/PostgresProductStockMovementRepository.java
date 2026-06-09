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
    public void createInCreditNote(Long productId,
                                   BigDecimal quantityIn,
                                   Long creditNoteItemId,
                                   BigDecimal unitCost,
                                   BigDecimal totalCost,
                                   BigDecimal balanceQty,
                                   BigDecimal balanceCost) {
        createMovement(productId, "IN_RETURN", "credit_note_item", creditNoteItemId,
                quantityIn, BigDecimal.ZERO, unitCost, totalCost, balanceQty, balanceCost);
    }

    @Override
    public void createOutCreditNoteRejection(Long productId,
                                             BigDecimal quantityOut,
                                             Long creditNoteItemId,
                                             BigDecimal unitCost,
                                             BigDecimal totalCost,
                                             BigDecimal balanceQty,
                                             BigDecimal balanceCost) {
        createMovement(productId, "OUT_CREDIT_NOTE_REJECTED", "credit_note_item", creditNoteItemId,
                BigDecimal.ZERO, quantityOut, unitCost, totalCost, balanceQty, balanceCost);
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

    @Override
    public void createOutProformaInternal(Long productId,
                                          BigDecimal quantityOut,
                                          Long proformaItemId,
                                          BigDecimal unitCost,
                                          BigDecimal totalCost,
                                          BigDecimal balanceQty,
                                          BigDecimal balanceCost) {
        createMovement(productId, "OUT_PROFORMA_INTERNAL", "proforma_item", proformaItemId,
                BigDecimal.ZERO, quantityOut, unitCost, totalCost, balanceQty, balanceCost);
    }

    @Override
    public void createInProformaInternalReturn(Long productId,
                                               BigDecimal quantityIn,
                                               Long proformaItemId,
                                               BigDecimal unitCost,
                                               BigDecimal totalCost,
                                               BigDecimal balanceQty,
                                               BigDecimal balanceCost) {
        createMovement(productId, "IN_PROFORMA_INTERNAL_RETURN", "proforma_item", proformaItemId,
                quantityIn, BigDecimal.ZERO, unitCost, totalCost, balanceQty, balanceCost);
    }

    @Override
    public boolean existsOutProformaInternal(Long proformaItemId) {
        return existsMovement("OUT_PROFORMA_INTERNAL", "proforma_item", proformaItemId);
    }

    @Override
    public boolean existsInProformaInternalReturn(Long proformaItemId) {
        return existsMovement("IN_PROFORMA_INTERNAL_RETURN", "proforma_item", proformaItemId);
    }

    @Override
    public boolean existsOutboundSaleItem(Long saleItemId) {
        if (saleItemId == null) return false;

        String sql = """
            SELECT COUNT(1)
            FROM product_stock_movement
            WHERE movement_type IN ('OUT_SALE', 'OUT_SALE_EDIT')
              AND source_table = 'sale_item'
              AND source_id = ?
        """;

        Long count = jdbcClient.sql(sql)
                .param(saleItemId)
                .query(Long.class)
                .single();

        return count != null && count > 0;
    }

    @Override
    public boolean existsInReturnSaleItem(Long saleItemId) {
        return existsMovement("IN_RETURN", "sale_item", saleItemId);
    }

    @Override
    public boolean existsOutCreditNoteRejection(Long creditNoteItemId) {
        return existsMovement("OUT_CREDIT_NOTE_REJECTED", "credit_note_item", creditNoteItemId);
    }

    private boolean existsMovement(String movementType, String sourceTable, Long sourceId) {
        if (sourceId == null) return false;

        String sql = """
            SELECT COUNT(1)
            FROM product_stock_movement
            WHERE movement_type = ?
              AND source_table = ?
              AND source_id = ?
        """;

        Long count = jdbcClient.sql(sql)
                .params(movementType, sourceTable, sourceId)
                .query(Long.class)
                .single();

        return count != null && count > 0;
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

package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;

public interface ProductStockMovementRepository {
    void createOutSale(Long productId,
                       BigDecimal quantityOut,
                       Long saleItemId,
                       BigDecimal unitCost,
                       BigDecimal totalCost,
                       BigDecimal balanceQty,
                       BigDecimal balanceCost);

    /**
     * Movimiento de salida por edición administrativa de una venta antes de SUNAT.
     */
    void createOutEdit(Long productId,
                       BigDecimal quantityOut,
                       Long saleItemId,
                       BigDecimal unitCost,
                       BigDecimal totalCost,
                       BigDecimal balanceQty,
                       BigDecimal balanceCost);

    /**
     * Movimiento de reversa por anulación/devolución de una venta.
     */
    void createInReturn(Long productId,
                        BigDecimal quantityIn,
                        Long saleItemId,
                        BigDecimal unitCost,
                        BigDecimal totalCost,
                        BigDecimal balanceQty,
                        BigDecimal balanceCost);

    void createInCreditNote(Long productId,
                            BigDecimal quantityIn,
                            Long creditNoteItemId,
                            BigDecimal unitCost,
                            BigDecimal totalCost,
                            BigDecimal balanceQty,
                            BigDecimal balanceCost);

    /**
     * Movimiento de ingreso por edición administrativa de una venta antes de SUNAT.
     */
    void createInEdit(Long productId,
                      BigDecimal quantityIn,
                      Long saleItemId,
                      BigDecimal unitCost,
                      BigDecimal totalCost,
                      BigDecimal balanceQty,
                      BigDecimal balanceCost);


    /**
     * Movimiento de salida para productos internos/no facturables
     * consumidos al convertir una proforma a venta.
     */
    void createOutProformaInternal(Long productId,
                                   BigDecimal quantityOut,
                                   Long proformaItemId,
                                   BigDecimal unitCost,
                                   BigDecimal totalCost,
                                   BigDecimal balanceQty,
                                   BigDecimal balanceCost);

    /**
     * Movimiento de reversa para devolver stock interno/no facturable
     * cuando se anula una venta creada desde proforma.
     */
    void createInProformaInternalReturn(Long productId,
                                        BigDecimal quantityIn,
                                        Long proformaItemId,
                                        BigDecimal unitCost,
                                        BigDecimal totalCost,
                                        BigDecimal balanceQty,
                                        BigDecimal balanceCost);

    boolean existsOutProformaInternal(Long proformaItemId);

}

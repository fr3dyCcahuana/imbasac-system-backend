package com.paulfernandosr.possystembackend.stockreservation.domain.port.output;

import com.paulfernandosr.possystembackend.stockreservation.domain.model.StockReservationReassignment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ProductStockReservationRepository {
    void replaceActiveForProforma(Long proformaId, Long reservedBy);
    void consumeActiveForProforma(Long proformaId);
    void releaseActiveForProforma(Long proformaId);
    /** Locks stock, checks exact donor quantities, records reassignment, and consumes own reservations. */
    void prepareStockForSale(Map<Long, BigDecimal> quantitiesByProduct, Long sourceProformaId,
                             List<StockReservationReassignment> reassignments, Long saleId, Long actorId);
    int expirePreviousDays();
}

package com.paulfernandosr.possystembackend.stockreservation.domain.port.output;

public interface ProductStockReservationRepository {
    void replaceActiveForProforma(Long proformaId, Long reservedBy);
    void consumeActiveForProforma(Long proformaId);
    void releaseActiveForProforma(Long proformaId);
    int expirePreviousDays();
}

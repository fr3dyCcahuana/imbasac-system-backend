package com.paulfernandosr.possystembackend.stock.domain.port.input;

import java.math.BigDecimal;

public interface StockService {

    void registerInbound(
            Long productId,
            BigDecimal quantity,
            BigDecimal unitCost,
            String movementType,
            String sourceTable,
            Long sourceId
    );

    void registerOutbound(
            Long productId,
            BigDecimal quantity,
            BigDecimal unitCost,   // opcional: puedes ignorar y usar averageCost actual
            String movementType,
            String sourceTable,
            Long sourceId
    );
}

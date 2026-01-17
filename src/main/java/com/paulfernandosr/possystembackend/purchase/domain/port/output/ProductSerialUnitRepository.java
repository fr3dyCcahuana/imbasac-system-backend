package com.paulfernandosr.possystembackend.purchase.domain.port.output;

import com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit;
import com.paulfernandosr.possystembackend.purchase.domain.model.SerialIdentifierConflict;

import java.util.List;
import java.util.Set;

public interface ProductSerialUnitRepository {

    List<SerialIdentifierConflict> findExistingIdentifiers(
            Set<String> vins,
            Set<String> engineNumbers,
            Set<String> serialNumbers
    );

    void insertInboundSerialUnits(
            Long purchaseItemId,
            Long productId,
            List<PurchaseSerialUnit> serialUnits
    );
}

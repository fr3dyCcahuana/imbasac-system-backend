package com.paulfernandosr.possystembackend.purchase.domain.port.output;

import com.paulfernandosr.possystembackend.purchase.domain.PurchaseSerialUnit;
import com.paulfernandosr.possystembackend.purchase.domain.model.SerialIdentifierConflict;

import java.util.List;
import java.util.Set;

public interface ProductSerialUnitRepository {

    List<SerialIdentifierConflict> findExistingIdentifiers(
            Set<String> vins,
            Set<String> engineNumbers,
            Set<String> chassisNumbers
    );

    List<SerialIdentifierConflict> findExistingIdentifiersExcluding(
            Set<String> vins,
            Set<String> engineNumbers,
            Set<String> chassisNumbers,
            Set<Long> excludedSerialUnitIds
    );

    void insertInboundSerialUnits(
            Long purchaseItemId,
            Long productId,
            List<PurchaseSerialUnit> serialUnits
    );

    void updateInboundSerialUnit(PurchaseSerialUnit serialUnit);

    int countBlockedSerialUnitsByPurchaseItemId(Long purchaseItemId);

    int countBlockedSerialUnitsByPurchaseId(Long purchaseId);

    void markSerialUnitsByPurchaseItemAsBaja(Long purchaseItemId);
}

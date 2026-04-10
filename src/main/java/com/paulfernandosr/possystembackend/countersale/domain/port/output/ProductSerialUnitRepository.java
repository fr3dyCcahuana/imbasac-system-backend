package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import com.paulfernandosr.possystembackend.countersale.domain.model.SerialUnit;

import java.util.List;

public interface ProductSerialUnitRepository {
    List<SerialUnit> lockByIds(List<Long> serialUnitIds);
    List<SerialUnit> lockByCounterSaleItemIds(List<Long> counterSaleItemIds);
    void markAsSold(Long serialUnitId);
    void releaseAfterVoid(Long serialUnitId);
}

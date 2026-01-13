package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.SerialUnit;

import java.util.List;

public interface ProductSerialUnitRepository {
    List<SerialUnit> lockByIds(List<Long> serialUnitIds);
    void markAsSold(Long serialUnitId, Long saleItemId);
}

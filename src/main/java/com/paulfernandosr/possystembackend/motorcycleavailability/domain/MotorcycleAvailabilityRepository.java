package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import java.util.List;
import java.util.Optional;

public interface MotorcycleAvailabilityRepository {
    MotorcycleAvailabilityPage findPage(MotorcycleAvailabilityQuery query);

    MotorcycleAvailabilityFilterOptions findFilterOptions();

    List<MotorcycleAvailabilityUnit> findUnits(Long productId);

    Optional<MotorcycleAvailabilityUnit> findUnit(Long productId, Long serialUnitId);
}

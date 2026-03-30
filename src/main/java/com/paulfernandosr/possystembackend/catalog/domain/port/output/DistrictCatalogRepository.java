package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.District;

import java.util.Collection;

public interface DistrictCatalogRepository {
    Collection<District> findByProvinceCode(String provinceCode);
}

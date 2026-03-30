package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.Province;

import java.util.Collection;

public interface ProvinceCatalogRepository {
    Collection<Province> findByDepartmentCode(String departmentCode);
}

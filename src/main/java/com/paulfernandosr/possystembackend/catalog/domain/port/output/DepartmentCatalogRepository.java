package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.Department;

import java.util.Collection;

public interface DepartmentCatalogRepository {
    Collection<Department> findAll();
}

package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.Department;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.DepartmentCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentCatalogRepository departmentCatalogRepository;

    public Collection<Department> findAll() {
        return departmentCatalogRepository.findAll();
    }
}

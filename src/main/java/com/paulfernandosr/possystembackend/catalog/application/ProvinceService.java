package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.Province;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.ProvinceCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class ProvinceService {

    private final ProvinceCatalogRepository provinceCatalogRepository;

    public Collection<Province> findByDepartmentCode(String departmentCode) {
        return provinceCatalogRepository.findByDepartmentCode(departmentCode);
    }
}

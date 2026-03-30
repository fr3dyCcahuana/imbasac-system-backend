package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.District;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.DistrictCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class DistrictService {

    private final DistrictCatalogRepository districtCatalogRepository;

    public Collection<District> findByProvinceCode(String provinceCode) {
        return districtCatalogRepository.findByProvinceCode(provinceCode);
    }
}

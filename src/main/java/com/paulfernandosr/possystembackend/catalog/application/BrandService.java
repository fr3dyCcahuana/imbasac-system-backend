package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.Brand;
import com.paulfernandosr.possystembackend.catalog.domain.exception.CatalogItemAlreadyExistsException;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.BrandCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandCatalogRepository brandCatalogRepository;

    public void create(Brand brand) {
        boolean exists = brandCatalogRepository.existsByName(brand.getName());
        if (exists) {
            throw new CatalogItemAlreadyExistsException("Brand already exists with name: " + brand.getName());
        }
        brandCatalogRepository.create(brand);
    }

    public Collection<Brand> findAll() {
        return brandCatalogRepository.findAll();
    }
}

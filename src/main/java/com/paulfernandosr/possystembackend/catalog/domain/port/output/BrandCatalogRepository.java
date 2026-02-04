package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.Brand;

import java.util.Collection;

public interface BrandCatalogRepository {
    void create(Brand brand);
    Collection<Brand> findAll();
    boolean existsByName(String name);
}

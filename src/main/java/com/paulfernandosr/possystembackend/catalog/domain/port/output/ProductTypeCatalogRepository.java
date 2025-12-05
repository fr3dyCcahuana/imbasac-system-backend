package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.ProductType;

import java.util.Collection;

public interface ProductTypeCatalogRepository {

    void create(ProductType productType);

    Collection<ProductType> findAll();

    boolean existsByName(String name);
}

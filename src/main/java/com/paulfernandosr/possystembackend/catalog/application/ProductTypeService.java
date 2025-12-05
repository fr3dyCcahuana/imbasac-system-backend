package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.ProductType;
import com.paulfernandosr.possystembackend.catalog.domain.exception.CatalogItemAlreadyExistsException;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.ProductTypeCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class ProductTypeService {

    private final ProductTypeCatalogRepository productTypeCatalogRepository;

    public void create(ProductType productType) {
        boolean exists = productTypeCatalogRepository.existsByName(productType.getName());

        if (exists) {
            throw new CatalogItemAlreadyExistsException(
                    "Product type already exists with name: " + productType.getName()
            );
        }

        productTypeCatalogRepository.create(productType);
    }

    public Collection<ProductType> findAll() {
        return productTypeCatalogRepository.findAll();
    }
}

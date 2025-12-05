package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.Compatibility;
import com.paulfernandosr.possystembackend.catalog.domain.exception.CatalogItemAlreadyExistsException;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.CompatibilityCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class CompatibilityService {

    private final CompatibilityCatalogRepository compatibilityCatalogRepository;

    public void create(Compatibility compatibility) {
        boolean exists = compatibilityCatalogRepository.existsByName(compatibility.getName());

        if (exists) {
            throw new CatalogItemAlreadyExistsException(
                    "Compatibility already exists with name: " + compatibility.getName()
            );
        }

        compatibilityCatalogRepository.create(compatibility);
    }

    public Collection<Compatibility> findAll() {
        return compatibilityCatalogRepository.findAll();
    }
}

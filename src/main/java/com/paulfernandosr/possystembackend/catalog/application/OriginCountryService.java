package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.OriginCountry;
import com.paulfernandosr.possystembackend.catalog.domain.exception.CatalogItemAlreadyExistsException;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.OriginCountryCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class OriginCountryService {

    private final OriginCountryCatalogRepository originCountryCatalogRepository;

    public void create(OriginCountry originCountry) {
        boolean exists = originCountryCatalogRepository.existsByName(originCountry.getName());

        if (exists) {
            throw new CatalogItemAlreadyExistsException(
                    "Origin country already exists with name: " + originCountry.getName()
            );
        }

        originCountryCatalogRepository.create(originCountry);
    }

    public Collection<OriginCountry> findAll() {
        return originCountryCatalogRepository.findAll();
    }
}

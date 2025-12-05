package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.OriginCountry;

import java.util.Collection;

public interface OriginCountryCatalogRepository {

    void create(OriginCountry originCountry);

    Collection<OriginCountry> findAll();

    boolean existsByName(String name);
}

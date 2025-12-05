package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.Compatibility;

import java.util.Collection;

public interface CompatibilityCatalogRepository {

    void create(Compatibility compatibility);

    Collection<Compatibility> findAll();

    boolean existsByName(String name);
}

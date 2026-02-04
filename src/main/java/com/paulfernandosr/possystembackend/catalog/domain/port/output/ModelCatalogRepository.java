package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.Model;

import java.util.Collection;

public interface ModelCatalogRepository {
    void create(Model model);
    Collection<Model> findAll();
    boolean existsByName(String name);
}

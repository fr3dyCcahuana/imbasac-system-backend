package com.paulfernandosr.possystembackend.catalog.domain.port.output;

import com.paulfernandosr.possystembackend.catalog.domain.Presentation;

import java.util.Collection;

public interface PresentationCatalogRepository {

    void create(Presentation presentation);

    Collection<Presentation> findAll();

    boolean existsByName(String name);
}

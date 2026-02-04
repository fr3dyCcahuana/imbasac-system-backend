package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.Model;
import com.paulfernandosr.possystembackend.catalog.domain.exception.CatalogItemAlreadyExistsException;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.ModelCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelCatalogRepository modelCatalogRepository;

    public void create(Model model) {
        boolean exists = modelCatalogRepository.existsByName(model.getName());
        if (exists) {
            throw new CatalogItemAlreadyExistsException("Model already exists with name: " + model.getName());
        }
        modelCatalogRepository.create(model);
    }

    public Collection<Model> findAll() {
        return modelCatalogRepository.findAll();
    }
}

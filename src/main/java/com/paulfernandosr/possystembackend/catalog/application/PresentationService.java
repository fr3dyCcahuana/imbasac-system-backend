package com.paulfernandosr.possystembackend.catalog.application;

import com.paulfernandosr.possystembackend.catalog.domain.Presentation;
import com.paulfernandosr.possystembackend.catalog.domain.exception.CatalogItemAlreadyExistsException;
import com.paulfernandosr.possystembackend.catalog.domain.port.output.PresentationCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class PresentationService {

    private final PresentationCatalogRepository presentationCatalogRepository;

    public void create(Presentation presentation) {
        boolean exists = presentationCatalogRepository.existsByName(presentation.getName());

        if (exists) {
            throw new CatalogItemAlreadyExistsException(
                    "Presentation already exists with name: " + presentation.getName()
            );
        }

        presentationCatalogRepository.create(presentation);
    }

    public Collection<Presentation> findAll() {
        return presentationCatalogRepository.findAll();
    }
}

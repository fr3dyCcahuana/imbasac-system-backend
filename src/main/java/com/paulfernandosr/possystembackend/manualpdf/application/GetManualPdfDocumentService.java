package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfDocumentNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.GetManualPdfDocumentUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetManualPdfDocumentService implements GetManualPdfDocumentUseCase {

    private final ManualPdfRepository repository;

    @Override
    public ManualPdfDocument getDocument(int year, Long modelId) {
        return repository.findBestDocumentByYearAndModel(year, modelId)
                .orElseThrow(() -> new ManualPdfDocumentNotFoundException(
                        "No se encontró PDF para el año y vehículo seleccionado."
                ));
    }
}

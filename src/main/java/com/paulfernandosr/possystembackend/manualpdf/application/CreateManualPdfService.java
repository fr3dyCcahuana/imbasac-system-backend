package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfDocument;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelStorageContext;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfRegistrationCommand;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfStoredFile;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.DuplicateManualPdfException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.InvalidManualPdfException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfCatalogNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.CreateManualPdfUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfFileRepository;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CreateManualPdfService implements CreateManualPdfUseCase {

    private final ManualPdfRepository repository;
    private final ManualPdfFileRepository fileRepository;

    @Override
    @Transactional
    public ManualPdfDocument create(ManualPdfRegistrationCommand command, MultipartFile file) {
        validate(command, file);

        ManualPdfModelStorageContext storageContext = repository.findModelStorageContextById(command.modelId())
                .orElseThrow(() -> new ManualPdfCatalogNotFoundException("No se encontró el modelo indicado."));

        if (repository.existsOverlappingDocument(command.modelId(), command.yearFrom(), command.yearTo())) {
            throw new DuplicateManualPdfException("Ya existe un PDF registrado para el mismo modelo y rango de años.");
        }

        String title = normalizeTitle(command.title(), storageContext.modelName(), command.yearFrom(), command.yearTo());
        boolean enabled = command.enabled() == null || command.enabled();

        ManualPdfStoredFile storedFile = fileRepository.store(
                storageContext.familyCode(),
                storageContext.modelCode(),
                command.yearFrom(),
                command.yearTo(),
                file
        );

        try {
            ManualPdfDocument document = new ManualPdfDocument(
                    null,
                    storageContext.modelId(),
                    title,
                    command.yearFrom(),
                    command.yearTo(),
                    storedFile.fileName(),
                    storedFile.fileKey(),
                    storedFile.mimeType(),
                    storedFile.fileSize(),
                    enabled
            );

            return repository.createDocument(document);
        } catch (RuntimeException ex) {
            fileRepository.deleteByKey(storedFile.fileKey());
            throw ex;
        }
    }

    private void validate(ManualPdfRegistrationCommand command, MultipartFile file) {
        if (command == null) {
            throw new InvalidManualPdfException("La solicitud del PDF es obligatoria.");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidManualPdfException("El archivo PDF es obligatorio.");
        }
        if (command.modelId() == null) {
            throw new InvalidManualPdfException("modelId es obligatorio.");
        }
        if (command.yearFrom() == null) {
            throw new InvalidManualPdfException("yearFrom es obligatorio.");
        }
        if (command.yearTo() == null) {
            throw new InvalidManualPdfException("yearTo es obligatorio.");
        }
        if (command.yearFrom() > command.yearTo()) {
            throw new InvalidManualPdfException("yearFrom no puede ser mayor que yearTo.");
        }
    }

    private String normalizeTitle(String title, String modelName, Integer yearFrom, Integer yearTo) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String yearPart = yearFrom.equals(yearTo) ? String.valueOf(yearFrom) : yearFrom + "-" + yearTo;
        return modelName.trim() + " " + yearPart;
    }
}

package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.*;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.InvalidManualPdfException;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.CreateManualPdfUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfFileRepository;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CreateManualPdfService implements CreateManualPdfUseCase {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern NON_FILE = Pattern.compile("[^a-z0-9]+");

    private final ManualPdfRepository repository;
    private final ManualPdfFileRepository fileRepository;

    @Override
    @Transactional
    public ManualPdfDocument create(ManualPdfRegistrationCommand command, MultipartFile file) {
        validate(command, file);

        String familyCode = normalizeCode(command.familyCode(), command.familyName());
        String modelCode = normalizeCode(command.modelCode(), command.modelName());
        String title = normalizeTitle(command.title(), command.modelName(), command.yearFrom(), command.yearTo());
        boolean enabled = command.enabled() == null || command.enabled();

        ManualPdfStoredFile storedFile = fileRepository.store(
                familyCode,
                modelCode,
                command.yearFrom(),
                command.yearTo(),
                file
        );

        try {
            ManualPdfFamily family = repository.upsertFamily(
                    familyCode,
                    command.familyName().trim(),
                    command.familySortOrder() != null ? command.familySortOrder() : 0
            );

            ManualPdfModel model = repository.upsertModel(
                    family.id(),
                    modelCode,
                    command.modelName().trim(),
                    command.modelSortOrder() != null ? command.modelSortOrder() : 0
            );

            ManualPdfDocument document = new ManualPdfDocument(
                    null,
                    model.id(),
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
        if (command.familyName() == null || command.familyName().isBlank()) {
            throw new InvalidManualPdfException("familyName es obligatorio.");
        }
        if (command.modelName() == null || command.modelName().isBlank()) {
            throw new InvalidManualPdfException("modelName es obligatorio.");
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

    private String normalizeCode(String code, String fallbackName) {
        String raw = (code != null && !code.isBlank()) ? code : fallbackName;
        String normalized = stripAccents(raw == null ? "" : raw.trim()).toUpperCase(Locale.ROOT);
        normalized = NON_ALNUM.matcher(normalized).replaceAll("-");
        normalized = normalized.replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank()) {
            throw new InvalidManualPdfException("No se pudo generar un código válido para familia/modelo.");
        }
        return normalized;
    }

    private String normalizeTitle(String title, String modelName, Integer yearFrom, Integer yearTo) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String yearPart = yearFrom.equals(yearTo) ? String.valueOf(yearFrom) : yearFrom + "-" + yearTo;
        return modelName.trim() + " " + yearPart;
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}

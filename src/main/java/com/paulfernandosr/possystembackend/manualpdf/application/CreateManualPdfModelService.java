package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModel;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfModelUpsertCommand;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.InvalidManualPdfException;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.ManualPdfCatalogNotFoundException;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.CreateManualPdfModelUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CreateManualPdfModelService implements CreateManualPdfModelUseCase {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]+");

    private final ManualPdfRepository repository;

    @Override
    @Transactional
    public ManualPdfModel create(ManualPdfModelUpsertCommand command) {
        if (command == null) {
            throw new InvalidManualPdfException("La solicitud de modelo es obligatoria.");
        }
        if (command.familyId() == null) {
            throw new InvalidManualPdfException("familyId es obligatorio.");
        }
        if (command.name() == null || command.name().isBlank()) {
            throw new InvalidManualPdfException("name es obligatorio.");
        }
        if (!repository.familyExists(command.familyId())) {
            throw new ManualPdfCatalogNotFoundException("No se encontró la familia indicada.");
        }

        String code = normalizeCode(command.code(), command.name());
        return repository.upsertModel(
                command.familyId(),
                code,
                command.name().trim(),
                command.sortOrder() != null ? command.sortOrder() : 0,
                command.enabled() == null || command.enabled()
        );
    }

    private String normalizeCode(String code, String fallbackName) {
        String raw = (code != null && !code.isBlank()) ? code : fallbackName;
        String normalized = stripAccents(raw.trim()).toUpperCase(Locale.ROOT);
        normalized = NON_ALNUM.matcher(normalized).replaceAll("-");
        normalized = normalized.replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank()) {
            throw new InvalidManualPdfException("No se pudo generar un código válido para el modelo.");
        }
        return normalized;
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}

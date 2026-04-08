package com.paulfernandosr.possystembackend.manualpdf.application;

import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamily;
import com.paulfernandosr.possystembackend.manualpdf.domain.ManualPdfFamilyUpsertCommand;
import com.paulfernandosr.possystembackend.manualpdf.domain.exception.InvalidManualPdfException;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.input.CreateManualPdfFamilyUseCase;
import com.paulfernandosr.possystembackend.manualpdf.domain.port.output.ManualPdfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CreateManualPdfFamilyService implements CreateManualPdfFamilyUseCase {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]+");

    private final ManualPdfRepository repository;

    @Override
    @Transactional
    public ManualPdfFamily create(ManualPdfFamilyUpsertCommand command) {
        if (command == null) {
            throw new InvalidManualPdfException("La solicitud de familia es obligatoria.");
        }
        if (command.name() == null || command.name().isBlank()) {
            throw new InvalidManualPdfException("name es obligatorio.");
        }

        String code = normalizeCode(command.code(), command.name());
        return repository.upsertFamily(
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
            throw new InvalidManualPdfException("No se pudo generar un código válido para la familia.");
        }
        return normalized;
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}

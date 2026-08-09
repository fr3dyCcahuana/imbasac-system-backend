package com.paulfernandosr.possystembackend.common.infrastructure.sunat;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SunatProductCodeValidator {

    private static final String CATALOG_25_CODES_FILE = "sunat-catalog-25-codes.txt";
    private static final Pattern SUNAT_PRODUCT_CODE_PATTERN = Pattern.compile("^[0-9]{8}$");
    private static final Set<String> CATALOG_25_CODES = loadCatalog25Codes();

    private SunatProductCodeValidator() {
    }

    public static String normalizeOptional(String code, String context) {
        String normalized = normalize(code);
        if (normalized.isBlank()) {
            return null;
        }
        return requireValid(normalized, context);
    }

    public static String requireValid(String code, String context) {
        String normalized = normalize(code);
        if (normalized.isBlank()) {
            throw invalid(context, "no esta informado");
        }

        if (!SUNAT_PRODUCT_CODE_PATTERN.matcher(normalized).matches()) {
            throw invalid(context, "debe tener exactamente 8 digitos numericos");
        }

        if (!CATALOG_25_CODES.contains(normalized)) {
            throw invalid(context, "no existe en el Catalogo 25 SUNAT/UNSPSC");
        }

        return normalized;
    }

    public static boolean isValid(String code) {
        String normalized = normalize(code);
        return SUNAT_PRODUCT_CODE_PATTERN.matcher(normalized).matches()
                && CATALOG_25_CODES.contains(normalized);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim();
    }

    private static IllegalArgumentException invalid(String context, String reason) {
        String safeContext = context == null || context.isBlank() ? "Codigo de Producto SUNAT" : context;
        return new IllegalArgumentException(safeContext + " invalido: " + reason + ".");
    }

    private static Set<String> loadCatalog25Codes() {
        try {
            ClassPathResource resource = new ClassPathResource(CATALOG_25_CODES_FILE);
            if (!resource.exists()) {
                throw new IllegalStateException("No se encontro " + CATALOG_25_CODES_FILE + " en classpath");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.US_ASCII))) {
                Set<String> codes = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .collect(Collectors.toUnmodifiableSet());

                if (codes.isEmpty()) {
                    throw new IllegalStateException(CATALOG_25_CODES_FILE + " esta vacio");
                }

                return codes;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cargar Catalogo 25 SUNAT", ex);
        }
    }
}

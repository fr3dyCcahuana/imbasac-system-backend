package com.paulfernandosr.possystembackend.manualpdf.infrastructure.util;

import java.text.Normalizer;
import java.util.Locale;

public final class ManualPdfTextUtils {

    private ManualPdfTextUtils() {
    }

    public static String requireTrimmed(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " es obligatorio.");
        }
        return value.trim();
    }

    public static String normalizeName(String value) {
        String trimmed = requireTrimmed(value, "name");
        return Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String normalizeFamilyCode(String code, String fallbackName) {
        String source = (code == null || code.isBlank()) ? fallbackName : code;
        return Normalizer.normalize(requireTrimmed(source, "familyCode"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    public static String normalizeModelCode(String code, String fallbackName) {
        String source = (code == null || code.isBlank()) ? fallbackName : code;
        return Normalizer.normalize(requireTrimmed(source, "modelCode"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    public static String sanitizeFileName(String fileName, String fallbackBaseName) {
        String source = (fileName == null || fileName.isBlank()) ? fallbackBaseName : fileName;
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String cleaned = normalized.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        return cleaned.isBlank() ? fallbackBaseName : cleaned;
    }
}

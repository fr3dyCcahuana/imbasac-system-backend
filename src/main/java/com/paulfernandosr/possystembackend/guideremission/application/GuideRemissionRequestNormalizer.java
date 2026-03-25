package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GuideRemissionRequestNormalizer {

    public void normalize(GuideRemissionSubmission request) {
        if (request == null) {
            return;
        }

        normalizeItems(request.getItems());
        List<GuideRemissionRelatedDocument> resolved = resolveRelatedDocuments(
                request.getRelatedDocuments(),
                request.getRelatedDocumentTypeCode(),
                request.getRelatedDocumentSerie(),
                request.getRelatedDocumentNumero(),
                request.getItems()
        );
        request.setRelatedDocuments(resolved.isEmpty() ? null : resolved);
        applyLegacyCompatibility(request, resolved);
    }

    public void normalize(GuideRemissionFullFlowRequest request) {
        if (request == null) {
            return;
        }

        normalizeItems(request.getItems());
        List<GuideRemissionRelatedDocument> resolved = resolveRelatedDocuments(
                request.getRelatedDocuments(),
                request.getRelatedDocumentTypeCode(),
                request.getRelatedDocumentSerie(),
                request.getRelatedDocumentNumero(),
                request.getItems()
        );
        request.setRelatedDocuments(resolved.isEmpty() ? null : resolved);
        applyLegacyCompatibility(request, resolved);
    }

    private void normalizeItems(List<GuideRemissionItem> items) {
        if (items == null) {
            return;
        }

        for (GuideRemissionItem item : items) {
            if (item == null || item.getSourceLines() == null) {
                continue;
            }

            for (GuideRemissionItemSourceLine sourceLine : item.getSourceLines()) {
                if (sourceLine == null) {
                    continue;
                }
                sourceLine.setRelatedDocumentTypeCode(normalizeDocumentType(sourceLine.getRelatedDocumentTypeCode()));
            }
        }
    }

    private List<GuideRemissionRelatedDocument> resolveRelatedDocuments(List<GuideRemissionRelatedDocument> explicitDocuments,
                                                                        String legacyTypeCode,
                                                                        String legacySerie,
                                                                        String legacyNumero,
                                                                        List<GuideRemissionItem> items) {
        Map<String, GuideRemissionRelatedDocument> documents = new LinkedHashMap<>();

        if (explicitDocuments != null) {
            for (GuideRemissionRelatedDocument document : explicitDocuments) {
                add(documents, document);
            }
        }

        if (hasText(legacyTypeCode) || hasText(legacySerie) || hasText(legacyNumero)) {
            add(documents, GuideRemissionRelatedDocument.builder()
                    .documentTypeCode(legacyTypeCode)
                    .serie(legacySerie)
                    .numero(legacyNumero)
                    .build());
        }

        if (items != null) {
            for (GuideRemissionItem item : items) {
                if (item == null || item.getSourceLines() == null) {
                    continue;
                }

                for (GuideRemissionItemSourceLine sourceLine : item.getSourceLines()) {
                    if (sourceLine == null) {
                        continue;
                    }
                    add(documents, GuideRemissionRelatedDocument.builder()
                            .documentTypeCode(sourceLine.getRelatedDocumentTypeCode())
                            .serie(sourceLine.getRelatedDocumentSerie())
                            .numero(sourceLine.getRelatedDocumentNumero())
                            .build());
                }
            }
        }

        return new ArrayList<>(documents.values());
    }

    private void applyLegacyCompatibility(GuideRemissionSubmission request, List<GuideRemissionRelatedDocument> resolved) {
        GuideRemissionRelatedDocument first = first(resolved);
        if (first == null) {
            request.setRelatedDocumentTypeCode(normalizeDocumentType(request.getRelatedDocumentTypeCode()));
            return;
        }

        if (!hasText(request.getRelatedDocumentTypeCode())) {
            request.setRelatedDocumentTypeCode(first.getDocumentTypeCode());
        } else {
            request.setRelatedDocumentTypeCode(normalizeDocumentType(request.getRelatedDocumentTypeCode()));
        }

        if (!hasText(request.getRelatedDocumentSerie())) {
            request.setRelatedDocumentSerie(first.getSerie());
        }
        if (!hasText(request.getRelatedDocumentNumero())) {
            request.setRelatedDocumentNumero(first.getNumero());
        }
    }

    private void applyLegacyCompatibility(GuideRemissionFullFlowRequest request, List<GuideRemissionRelatedDocument> resolved) {
        GuideRemissionRelatedDocument first = first(resolved);
        if (first == null) {
            request.setRelatedDocumentTypeCode(normalizeDocumentType(request.getRelatedDocumentTypeCode()));
            return;
        }

        if (!hasText(request.getRelatedDocumentTypeCode())) {
            request.setRelatedDocumentTypeCode(first.getDocumentTypeCode());
        } else {
            request.setRelatedDocumentTypeCode(normalizeDocumentType(request.getRelatedDocumentTypeCode()));
        }

        if (!hasText(request.getRelatedDocumentSerie())) {
            request.setRelatedDocumentSerie(first.getSerie());
        }
        if (!hasText(request.getRelatedDocumentNumero())) {
            request.setRelatedDocumentNumero(first.getNumero());
        }
    }

    private GuideRemissionRelatedDocument first(List<GuideRemissionRelatedDocument> resolved) {
        return resolved == null || resolved.isEmpty() ? null : resolved.get(0);
    }

    private void add(Map<String, GuideRemissionRelatedDocument> documents, GuideRemissionRelatedDocument document) {
        if (document == null) {
            return;
        }

        String type = normalizeDocumentType(document.getDocumentTypeCode());
        String serie = trimToNull(document.getSerie());
        String numero = trimToNull(document.getNumero());

        if (!hasText(type) || !hasText(serie) || !hasText(numero)) {
            return;
        }

        String key = type + "|" + serie + "|" + numero;
        documents.putIfAbsent(key, GuideRemissionRelatedDocument.builder()
                .documentTypeCode(type)
                .serie(serie)
                .numero(numero)
                .build());
    }

    private String normalizeDocumentType(String value) {
        if (!hasText(value)) {
            return null;
        }

        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "01", "FACTURA" -> "01";
            case "03", "BOLETA", "BOLETA ELECTRONICA", "BOLETA VENTA ELECTRONICA" -> "03";
            default -> normalized;
        };
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

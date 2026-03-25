package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.InvalidGuideRemissionException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class GuideRemissionBusinessValidator {
    public void validate(GuideRemissionSubmission request) {
        validateGuideAndItems(request.getGuia(), request.getItems());
        validateRelatedDocuments(request.getRelatedDocuments(), request.getRelatedDocumentTypeCode(), request.getRelatedDocumentSerie(), request.getRelatedDocumentNumero(), request.getItems());
        require(request.getToken(), "La guía debe incluir token.");
    }

    public void validate(GuideRemissionFullFlowRequest request) {
        validateGuideAndItems(request.getGuia(), request.getItems());
        validateRelatedDocuments(request.getRelatedDocuments(), request.getRelatedDocumentTypeCode(), request.getRelatedDocumentSerie(), request.getRelatedDocumentNumero(), request.getItems());
    }

    private void validateGuideAndItems(GuideRemissionData guide, List<GuideRemissionItem> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidGuideRemissionException("La guía debe tener al menos un item.");
        }

        int itemIndex = 0;
        for (GuideRemissionItem item : items) {
            itemIndex++;
            require(item.getCantidad(), "Cada item debe incluir cantidad.");
            require(item.getDescripcion(), "Cada item debe incluir descripción.");
            require(item.getCodigo(), "Cada item debe incluir código.");
            require(item.getCodigoUnidad(), "Cada item debe incluir código de unidad.");
            parsePositiveNumber(item.getCantidad(), "La cantidad del item " + itemIndex + " debe ser mayor a cero.");

            validateItemSourceLines(item, itemIndex);
        }

        require(guide.getSerie(), "La guía debe incluir serie.");
        require(guide.getNumero(), "La guía debe incluir número.");
        require(guide.getFechaEmision(), "La guía debe incluir fecha de emisión.");
        require(guide.getHoraEmision(), "La guía debe incluir hora de emisión.");
        require(guide.getFechaTraslado(), "La guía debe incluir fecha de traslado.");
        require(guide.getGuiaMotivoTraslado(), "La guía debe incluir motivo de traslado.");
        require(guide.getGuiaModalidadTraslado(), "La guía debe incluir modalidad de traslado.");
        require(guide.getDestinatarioNumeroDocumento(), "La guía debe incluir documento del destinatario.");
        require(guide.getDestinatarioNombresRazon(), "La guía debe incluir nombre/razón social del destinatario.");
        require(guide.getPartidaUbigeo(), "La guía debe incluir ubigeo de partida.");
        require(guide.getPartidaDireccion(), "La guía debe incluir dirección de partida.");
        require(guide.getLlegadaUbigeo(), "La guía debe incluir ubigeo de llegada.");
        require(guide.getLlegadaDireccion(), "La guía debe incluir dirección de llegada.");
        require(guide.getPesoTotal(), "La guía debe incluir peso total.");
        parsePositiveNumber(guide.getPesoTotal(), "El peso total de la guía debe ser mayor a cero.");

        if ("04".equals(guide.getGuiaMotivoTraslado())) {
            require(guide.getPartidaCodigoEstablecimiento(), "Para traslado entre almacenes se requiere partida_codigo_establecimiento.");
            require(guide.getLlegadaCodigoEstablecimiento(), "Para traslado entre almacenes se requiere llegada_codigo_establecimiento.");
        }

        if ("01".equals(guide.getGuiaModalidadTraslado())) {
            boolean hasNewPublicFields = hasText(guide.getNumeroDocumentoTransporte()) && hasText(guide.getEntidadTransporte());
            boolean hasLegacyPublicFields = hasText(guide.getEntidadIdTransporte()) && hasText(guide.getNumeroMtcTransporte());

            if (!hasNewPublicFields && !hasLegacyPublicFields) {
                throw new InvalidGuideRemissionException(
                        "Para transporte público se requiere numero_documento_transporte y entidad_transporte, o la pareja heredada entidad_id_transporte y numero_mtc_transporte."
                );
            }
        }

        if ("02".equals(guide.getGuiaModalidadTraslado())) {
            require(guide.getConductorDni(), "Para transporte privado se requiere conductor_dni.");
            require(guide.getConductorNombres(), "Para transporte privado se requiere conductor_nombres.");
            require(guide.getConductorApellidos(), "Para transporte privado se requiere conductor_apellidos.");
            require(guide.getConductorLicencia(), "Para transporte privado se requiere conductor_licencia.");
            require(guide.getVehiculoPlaca(), "Para transporte privado se requiere vehiculo_placa.");
        }
    }

    private void validateItemSourceLines(GuideRemissionItem item, int itemIndex) {
        if (item.getSourceLines() == null || item.getSourceLines().isEmpty()) {
            return;
        }

        BigDecimal expectedQuantity = parsePositiveNumber(item.getCantidad(), "La cantidad del item " + itemIndex + " debe ser mayor a cero.");
        BigDecimal accumulated = BigDecimal.ZERO;

        int allocationIndex = 0;
        for (GuideRemissionItemSourceLine sourceLine : item.getSourceLines()) {
            allocationIndex++;
            require(sourceLine.getRelatedDocumentTypeCode(), "Cada asignación del item " + itemIndex + " debe incluir related_document_type_code.");
            require(sourceLine.getRelatedDocumentSerie(), "Cada asignación del item " + itemIndex + " debe incluir related_document_serie.");
            require(sourceLine.getRelatedDocumentNumero(), "Cada asignación del item " + itemIndex + " debe incluir related_document_numero.");
            validateDocumentType(sourceLine.getRelatedDocumentTypeCode(), "El tipo de comprobante de la asignación " + allocationIndex + " del item " + itemIndex + " no es válido.");
            accumulated = accumulated.add(parsePositiveNumber(
                    sourceLine.getCantidad(),
                    "La cantidad de la asignación " + allocationIndex + " del item " + itemIndex + " debe ser mayor a cero."
            ));
        }

        if (accumulated.compareTo(expectedQuantity) != 0) {
            throw new InvalidGuideRemissionException(
                    "La suma de asignaciones del item " + itemIndex + " debe coincidir exactamente con la cantidad del item."
            );
        }
    }

    private void validateRelatedDocuments(List<GuideRemissionRelatedDocument> relatedDocuments,
                                          String legacyTypeCode,
                                          String legacySerie,
                                          String legacyNumero,
                                          List<GuideRemissionItem> items) {
        Set<String> uniqueKeys = new HashSet<>();

        boolean hasLegacyAny = hasText(legacyTypeCode) || hasText(legacySerie) || hasText(legacyNumero);
        if (hasLegacyAny) {
            require(legacyTypeCode, "Si informa comprobante relacionado, debe incluir related_document_type_code.");
            require(legacySerie, "Si informa comprobante relacionado, debe incluir related_document_serie.");
            require(legacyNumero, "Si informa comprobante relacionado, debe incluir related_document_numero.");
            validateDocumentType(legacyTypeCode, "related_document_type_code no tiene un valor admitido.");
            if (relatedDocuments == null || relatedDocuments.isEmpty()) {
                uniqueKeys.add(buildDocumentKey(legacyTypeCode, legacySerie, legacyNumero));
            }
        }

        if (relatedDocuments != null) {
            int index = 0;
            for (GuideRemissionRelatedDocument document : relatedDocuments) {
                index++;
                require(document.getDocumentTypeCode(), "Cada comprobante relacionado debe incluir document_type_code.");
                require(document.getSerie(), "Cada comprobante relacionado debe incluir serie.");
                require(document.getNumero(), "Cada comprobante relacionado debe incluir numero.");
                validateDocumentType(document.getDocumentTypeCode(), "El tipo del comprobante relacionado " + index + " no es válido.");

                String key = buildDocumentKey(document.getDocumentTypeCode(), document.getSerie(), document.getNumero());
                if (!uniqueKeys.add(key)) {
                    throw new InvalidGuideRemissionException("No se permiten comprobantes relacionados duplicados en la guía.");
                }
            }
        }

        if (items == null) {
            return;
        }

        for (GuideRemissionItem item : items) {
            if (item == null || item.getSourceLines() == null) {
                continue;
            }

            for (GuideRemissionItemSourceLine sourceLine : item.getSourceLines()) {
                validateDocumentType(sourceLine.getRelatedDocumentTypeCode(), "El tipo de comprobante de una asignación no es válido.");
            }
        }
    }

    private void validateDocumentType(String typeCode, String message) {
        String normalizedType = normalizeDocumentType(typeCode);
        if (!("01".equals(normalizedType)
                || "03".equals(normalizedType)
                || "04".equals(normalizedType)
                || "07".equals(normalizedType)
                || "08".equals(normalizedType)
                || "09".equals(normalizedType)
                || "12".equals(normalizedType)
                || "31".equals(normalizedType))) {
            throw new InvalidGuideRemissionException(message);
        }
    }

    private String buildDocumentKey(String typeCode, String serie, String numero) {
        return normalizeDocumentType(typeCode) + "|" + serie.trim() + "|" + numero.trim();
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

    private BigDecimal parsePositiveNumber(String value, String message) {
        try {
            BigDecimal result = new BigDecimal(value.trim());
            if (result.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidGuideRemissionException(message);
            }
            return result;
        } catch (NumberFormatException ex) {
            throw new InvalidGuideRemissionException(message);
        }
    }

    private void require(String value, String message) {
        if (!hasText(value)) {
            throw new InvalidGuideRemissionException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

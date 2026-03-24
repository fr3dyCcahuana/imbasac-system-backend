package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.InvalidGuideRemissionException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GuideRemissionBusinessValidator {
    public void validate(GuideRemissionSubmission request) {
        validateGuideAndItems(request.getGuia(), request.getItems());
        validateRelatedDocument(request.getRelatedDocumentTypeCode(), request.getRelatedDocumentSerie(), request.getRelatedDocumentNumero());
        require(request.getToken(), "La guía debe incluir token.");
    }

    public void validate(GuideRemissionFullFlowRequest request) {
        validateGuideAndItems(request.getGuia(), request.getItems());
        validateRelatedDocument(request.getRelatedDocumentTypeCode(), request.getRelatedDocumentSerie(), request.getRelatedDocumentNumero());
    }

    private void validateGuideAndItems(GuideRemissionData guide, List<GuideRemissionItem> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidGuideRemissionException("La guía debe tener al menos un item.");
        }

        for (GuideRemissionItem item : items) {
            require(item.getCantidad(), "Cada item debe incluir cantidad.");
            require(item.getDescripcion(), "Cada item debe incluir descripción.");
            require(item.getCodigo(), "Cada item debe incluir código.");
            require(item.getCodigoUnidad(), "Cada item debe incluir código de unidad.");
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

    private void validateRelatedDocument(String typeCode, String serie, String numero) {
        boolean hasAny = hasText(typeCode) || hasText(serie) || hasText(numero);

        if (!hasAny) {
            return;
        }

        require(typeCode, "Si informa comprobante relacionado, debe incluir related_document_type_code.");
        require(serie, "Si informa comprobante relacionado, debe incluir related_document_serie.");
        require(numero, "Si informa comprobante relacionado, debe incluir related_document_numero.");

        String normalizedType = typeCode.trim().toUpperCase();
        if (!("01".equals(normalizedType) || "03".equals(normalizedType)
                || "FACTURA".equals(normalizedType)
                || "BOLETA".equals(normalizedType)
                || "BOLETA ELECTRONICA".equals(normalizedType)
                || "BOLETA VENTA ELECTRONICA".equals(normalizedType))) {
            throw new InvalidGuideRemissionException(
                    "related_document_type_code debe ser 01/FACTURA o 03/BOLETA."
            );
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

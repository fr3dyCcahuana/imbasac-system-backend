package com.paulfernandosr.possystembackend.salev2.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.documentseries.DocumentSeriesPolicy;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.EmitSaleV2ToSunatUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2SunatRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SunatEmissionResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.SaleV2SunatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmitSaleV2ToSunatService implements EmitSaleV2ToSunatUseCase {

    private static final String SUCCESS_RESPONSE = "0";

    private final SaleV2SunatRepository saleV2SunatRepository;
    private final DocumentSeriesPolicy documentSeriesPolicy;
    private final RestClient sunatRestClient;
    private final SunatProps sunatProps;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SaleV2SunatEmissionResponse emit(Long saleId) {
        if (saleId == null) {
            throw new InvalidSaleV2Exception("saleId es obligatorio.");
        }

        SaleV2SunatRepository.LockedSunatSale sale = saleV2SunatRepository.lockSale(saleId);
        if (sale == null) {
            throw new InvalidSaleV2Exception("Venta no encontrada: " + saleId);
        }

        validateSale(sale);

        if ("ACEPTADO".equalsIgnoreCase(blankIfNull(sale.getSunatStatus()))) {
            return buildResponse(sale.getSaleId(), sale.getDocType(), sale.getSeries(), sale.getNumber(), sale.getSunatStatus(),
                    sale.getSunatResponseCode(), sale.getSunatResponseDescription(), sale.getSunatHashCode(),
                    sale.getSunatXmlPath(), sale.getSunatCdrPath(), sale.getSunatPdfPath(), sale.getSunatSentAt());
        }

        List<SaleV2SunatRepository.SaleItemForSunat> items = saleV2SunatRepository.findItems(saleId);
        if (items.isEmpty()) {
            throw new InvalidSaleV2Exception("La venta no tiene ítems para emitir a SUNAT.");
        }

        List<SaleV2SunatRepository.SaleItemForSunat> visibleItems = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getVisibleInDocument()))
                .toList();

        if (visibleItems.isEmpty()) {
            throw new InvalidSaleV2Exception("La venta no tiene líneas visibles para SUNAT (visible_in_document=true).");
        }

        if (visibleItems.size() != items.size()) {
            throw new InvalidSaleV2Exception("La venta contiene líneas no visibles para SUNAT. Regulariza la venta antes de emitir para evitar descuadres entre cabecera e ítems.");
        }

        visibleItems.stream()
                .filter(i -> !"VENDIDO".equalsIgnoreCase(blankIfNull(i.getLineKind())))
                .findAny()
                .ifPresent(i -> {
                    throw new InvalidSaleV2Exception("La emisión SUNAT desacoplada no soporta obsequios u otros tipos de línea. Línea=" + i.getLineNumber());
                });

        DocumentRequest request = SaleV2SunatMapper.map(sunatProps, sale, visibleItems);

        log.info("SUNAT V2 request: {}", request);

        try {
            String rawResponse = sunatRestClient.post()
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("SUNAT V2 response: {}", rawResponse);

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode data = root != null ? root.path("data") : null;

            String providerError = textValue(data, "error");
            if (providerError != null && !providerError.isBlank()) {
                String finalStatus = "ERROR";
                LocalDateTime emittedAt = LocalDateTime.now();

                saleV2SunatRepository.updateEmissionResult(
                        saleId,
                        finalStatus,
                        null,
                        providerError,
                        null,
                        null,
                        null,
                        null,
                        emittedAt
                );

                return buildResponse(
                        saleId,
                        sale.getDocType(),
                        sale.getSeries(),
                        sale.getNumber(),
                        finalStatus,
                        null,
                        providerError,
                        null,
                        null,
                        null,
                        null,
                        emittedAt
                );
            }

            String code = textValue(data, "respuesta_sunat_codigo");
            String description = defaultIfBlank(textValue(data, "respuesta_sunat_descripcion"), "Respuesta vacía de SUNAT");String hashCode = extractHashCode(data != null ? data.path("codigo_hash") : null);
            String xmlPath = textValue(data, "ruta_xml");
            String cdrPath = textValue(data, "ruta_cdr");
            String pdfPath = textValue(data, "ruta_pdf");
            String finalStatus = SUCCESS_RESPONSE.equals(code) ? "ACEPTADO" : "RECHAZADO";
            LocalDateTime emittedAt = LocalDateTime.now();

            saleV2SunatRepository.updateEmissionResult(
                    saleId,
                    finalStatus,
                    code,
                    description,
                    hashCode,
                    xmlPath,
                    cdrPath,
                    pdfPath,
                    emittedAt
            );

            return buildResponse(saleId, sale.getDocType(), sale.getSeries(), sale.getNumber(), finalStatus,
                    code, description, hashCode, xmlPath, cdrPath, pdfPath, emittedAt);

        } catch (RuntimeException ex) {
            LocalDateTime emittedAt = LocalDateTime.now();
            saleV2SunatRepository.markEmissionError(saleId, ex.getMessage(), emittedAt);
            throw ex;
        } catch (Exception ex) {
            LocalDateTime emittedAt = LocalDateTime.now();
            saleV2SunatRepository.markEmissionError(saleId, ex.getMessage(), emittedAt);
            throw new InvalidSaleV2Exception("No se pudo interpretar la respuesta de SUNAT: " + ex.getMessage());
        }
    }

    private void validateSale(SaleV2SunatRepository.LockedSunatSale sale) {
        if (!"EMITIDA".equalsIgnoreCase(blankIfNull(sale.getStatus()))) {
            throw new InvalidSaleV2Exception("Solo se puede emitir a SUNAT una venta EMITIDA. Estado actual: " + sale.getStatus());
        }
        String docType = blankIfNull(sale.getDocType()).toUpperCase();
        if (!"BOLETA".equals(docType) && !"FACTURA".equals(docType)) {
            throw new InvalidSaleV2Exception("Solo BOLETA/FACTURA se envían a SUNAT. docType=" + sale.getDocType());
        }
        documentSeriesPolicy.requireAllowed(docType, sale.getSeries(), InvalidSaleV2Exception::new);
    }


    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private String extractHashCode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    String value = item.asText();
                    if (value != null && !value.trim().isEmpty()) {
                        return value;
                    }
                }
            }
            return null;
        }
        if (node.isObject()) {
            JsonNode codeNode = node.path("codigo");
            if (!codeNode.isMissingNode() && !codeNode.isNull()) {
                return codeNode.asText();
            }
            JsonNode hashNode = node.path("hash");
            if (!hashNode.isMissingNode() && !hashNode.isNull()) {
                return hashNode.asText();
            }
        }
        String value = node.asText();
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private SaleV2SunatEmissionResponse buildResponse(Long saleId, String docType, String series, Long number,
                                                      String sunatStatus, String code, String description,
                                                      String hashCode, String xmlPath, String cdrPath,
                                                      String pdfPath, LocalDateTime emittedAt) {
        return SaleV2SunatEmissionResponse.builder()
                .saleId(saleId)
                .docType(docType)
                .series(series)
                .number(number)
                .sunatStatus(sunatStatus)
                .sunatCode(code)
                .sunatDescription(description)
                .hashCode(hashCode)
                .xmlPath(xmlPath)
                .cdrPath(cdrPath)
                .pdfPath(pdfPath)
                .emittedAt(emittedAt)
                .build();
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}

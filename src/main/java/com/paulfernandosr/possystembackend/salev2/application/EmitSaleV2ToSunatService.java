package com.paulfernandosr.possystembackend.salev2.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentResponse;
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

        items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getVisibleInDocument()))
                .filter(i -> !"VENDIDO".equalsIgnoreCase(blankIfNull(i.getLineKind())))
                .findAny()
                .ifPresent(i -> {
                    throw new InvalidSaleV2Exception("La emisión SUNAT desacoplada no soporta obsequios u otros tipos de línea. Línea=" + i.getLineNumber());
                });

        if (!"GRAVADA".equalsIgnoreCase(blankIfNull(sale.getTaxStatus()))) {
            throw new InvalidSaleV2Exception("La emisión SUNAT desacoplada implementada en este parche soporta solo ventas GRAVADA.");
        }

        DocumentRequest request = SaleV2SunatMapper.map(sunatProps, sale, items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getVisibleInDocument()))
                .toList());

        log.info("SUNAT V2 request: {}", request);

        try {
            String rawResponse = sunatRestClient.post()
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("SUNAT V2 response: {}", rawResponse);

            DocumentResponse response = objectMapper.readValue(rawResponse, DocumentResponse.class);
            DocumentResponse.Data data = response != null ? response.getData() : null;
            String code = data != null ? data.getCode() : null;
            String description = data != null ? data.getDescription() : "Respuesta vacía de SUNAT";
            String hashCode = data != null && data.getHash() != null ? data.getHash().getCode() : null;
            String xmlPath = data != null ? data.getXmlPath() : null;
            String cdrPath = data != null ? data.getCdrPath() : null;
            String pdfPath = data != null ? data.getPdfPath() : null;
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

        } catch (JsonProcessingException ex) {
            LocalDateTime emittedAt = LocalDateTime.now();
            saleV2SunatRepository.markEmissionError(saleId, ex.getOriginalMessage(), emittedAt);
            throw new InvalidSaleV2Exception("No se pudo interpretar la respuesta de SUNAT: " + ex.getOriginalMessage());
        } catch (RuntimeException ex) {
            LocalDateTime emittedAt = LocalDateTime.now();
            saleV2SunatRepository.markEmissionError(saleId, ex.getMessage(), emittedAt);
            throw ex;
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

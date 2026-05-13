package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.common.infrastructure.documentseries.DocumentSeriesPolicy;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.EmitSaleV2ToSunatUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2SunatRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SunatEmissionResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.SaleV2SunatMapper;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatEmissionResult;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatEmissionResultParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmitSaleV2ToSunatService implements EmitSaleV2ToSunatUseCase {

    private static final BigDecimal GENERIC_CUSTOMER_TOTAL_LIMIT = new BigDecimal("700.00");

    private final SaleV2SunatRepository saleV2SunatRepository;
    private final DocumentSeriesPolicy documentSeriesPolicy;
    private final RestClient sunatRestClient;
    private final SunatProps sunatProps;
    private final SunatEmissionResultParser sunatEmissionResultParser;
    private final SaleV2SunatRelationFinalizerService relationFinalizerService;

    @Override
    @Transactional(noRollbackFor = Exception.class)
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
            return buildResponse(
                    sale.getSaleId(),
                    sale.getDocType(),
                    sale.getSeries(),
                    sale.getNumber(),
                    sale.getSunatStatus(),
                    sale.getSunatResponseCode(),
                    sale.getSunatResponseDescription(),
                    sale.getSunatHashCode(),
                    sale.getSunatXmlPath(),
                    sale.getSunatCdrPath(),
                    sale.getSunatPdfPath(),
                    sale.getSunatSentAt()
            );
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

        validateMotorcycleItems(visibleItems);

        DocumentRequest request = SaleV2SunatMapper.map(sunatProps, sale, visibleItems);

        log.info(
                "SUNAT V2 request: saleId={}, docType={}, series={}, number={}, total={}",
                sale.getSaleId(),
                sale.getDocType(),
                sale.getSeries(),
                sale.getNumber(),
                sale.getTotal()
        );

        try {
            String rawResponse = sunatRestClient.post()
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("SUNAT V2 response: {}", rawResponse);

            SunatEmissionResult result = sunatEmissionResultParser.parse(rawResponse, LocalDateTime.now());
            persistAndFinalize(saleId, sale, result);
            return buildResponse(sale, result);

        } catch (Exception ex) {
            SunatEmissionResult result = sunatEmissionResultParser.fromException(ex, LocalDateTime.now());
            persistAndFinalize(saleId, sale, result);
            return buildResponse(sale, result);
        }
    }

    private void persistAndFinalize(Long saleId,
                                    SaleV2SunatRepository.LockedSunatSale sale,
                                    SunatEmissionResult result) {
        saleV2SunatRepository.updateEmissionResult(
                saleId,
                result.getStatus(),
                result.getCode(),
                result.getDescription(),
                result.getHashCode(),
                result.getXmlPath(),
                result.getCdrPath(),
                result.getPdfPath(),
                result.getEmittedAt()
        );

        if (result.isAccepted()) {
            relationFinalizerService.onAccepted(
                    saleId,
                    sale.getDocType(),
                    sale.getSeries(),
                    sale.getNumber(),
                    result.getEmittedAt()
            );
        } else {
            relationFinalizerService.onNotAccepted(
                    saleId,
                    result.getStatus(),
                    result.getDescription()
            );
        }
    }

    private SaleV2SunatEmissionResponse buildResponse(SaleV2SunatRepository.LockedSunatSale sale,
                                                      SunatEmissionResult result) {
        return SaleV2SunatEmissionResponse.builder()
                .saleId(sale.getSaleId())
                .docType(sale.getDocType())
                .series(sale.getSeries())
                .number(sale.getNumber())
                .sunatStatus(result.getStatus())
                .sunatCode(result.getCode())
                .sunatDescription(result.getDescription())
                .hashCode(result.getHashCode())
                .xmlPath(result.getXmlPath())
                .cdrPath(result.getCdrPath())
                .pdfPath(result.getPdfPath())
                .emittedAt(result.getEmittedAt())
                .accepted(result.isAccepted())
                .rejected(result.isRejected())
                .communicationError(result.isCommunicationError())
                .retryable(result.isRetryable())
                .build();
    }

    private void validateSale(SaleV2SunatRepository.LockedSunatSale sale) {
        if (!"EMITIDA".equalsIgnoreCase(blankIfNull(sale.getStatus()))) {
            throw new InvalidSaleV2Exception("Solo se puede emitir a SUNAT una venta EMITIDA. Estado actual: " + sale.getStatus());
        }
        String docType = blankIfNull(sale.getDocType()).toUpperCase(Locale.ROOT);
        if (!"BOLETA".equals(docType) && !"FACTURA".equals(docType)) {
            throw new InvalidSaleV2Exception("Solo BOLETA/FACTURA se envían a SUNAT. docType=" + sale.getDocType());
        }
        documentSeriesPolicy.requireAllowed(docType, sale.getSeries(), InvalidSaleV2Exception::new);

        if (isGenericCustomerDocumentType(sale.getCustomerDocType())) {
            if ("FACTURA".equals(docType)) {
                throw new InvalidSaleV2Exception("FACTURA no permite cliente genérico. Debe usar RUC.");
            }
            if (sale.getTotal() == null) {
                throw new InvalidSaleV2Exception("No se pudo validar el total de la venta antes de SUNAT.");
            }
            if (sale.getTotal().compareTo(GENERIC_CUSTOMER_TOTAL_LIMIT) > 0) {
                throw new InvalidSaleV2Exception("No se puede enviar a SUNAT una venta diaria o venta rápida mayor a S/ 700.00 con cliente genérico.");
            }
        }
    }

    private boolean isGenericCustomerDocumentType(String value) {
        String v = blankIfNull(value).trim().toUpperCase();
        return switch (v) {
            case "GEN", "GENERICO", "GENÉRICO", "GENERAL", "0",
                 "OTROS", "SIN_DOCUMENTO", "SIN DOCUMENTO" -> true;
            default -> false;
        };
    }


    private void validateMotorcycleItems(List<SaleV2SunatRepository.SaleItemForSunat> items) {
        for (SaleV2SunatRepository.SaleItemForSunat item : items) {
            if (!isMotorcycleItem(item)) {
                continue;
            }

            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ONE) != 0) {
                throw new InvalidSaleV2Exception(
                        "Cada motocicleta debe emitirse con cantidad 1 para SUNAT. Línea=" + item.getLineNumber()
                );
            }

            requireMotorcycleValue(item.getBrand(), "Marca", item);
            requireMotorcycleValue(item.getModel(), "Modelo", item);
            requireMotorcycleValue(item.getVehicleClass(), "Clase/categoría vehicular", item);
            requireMotorcycleValue(item.getEngineNumber(), "Número de motor", item);
            requireMotorcycleValue(item.getChassisNumber(), "Número de chasis", item);
            requireMotorcycleValue(item.getVin(), "VIN", item);
            requireMotorcycleValue(item.getColor(), "Color", item);
            requireMotorcycleValue(item.getYearMake(), "Año fabricación", item);
            requireMotorcycleValue(item.getEngineCapacity(), "Capacidad motor", item);
            requireMotorcycleValue(item.getFuel(), "Combustible", item);
        }
    }

    private boolean isMotorcycleItem(SaleV2SunatRepository.SaleItemForSunat item) {
        String category = blankIfNull(item.getProductCategory()).trim().toUpperCase(Locale.ROOT);
        return category.contains("MOTOCIC")
                || "MOTO".equals(category)
                || "MOTOCICLETA".equals(category)
                || "MOTOCICLETAS".equals(category);
    }

    private void requireMotorcycleValue(Object value, String label, SaleV2SunatRepository.SaleItemForSunat item) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new InvalidSaleV2Exception(
                    "Falta dato de motocicleta para SUNAT: " + label + ". Línea=" + item.getLineNumber()
            );
        }
    }


    private SaleV2SunatEmissionResponse buildResponse(Long saleId, String docType, String series, Long number,
                                                      String sunatStatus, String code, String description,
                                                      String hashCode, String xmlPath, String cdrPath,
                                                      String pdfPath, LocalDateTime emittedAt) {
        String normalized = sunatStatus == null ? "" : sunatStatus.trim().toUpperCase(Locale.ROOT);

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
                .accepted("ACEPTADO".equals(normalized))
                .rejected("RECHAZADO".equals(normalized))
                .communicationError("ERROR_COMUNICACION".equals(normalized))
                .retryable("ERROR_COMUNICACION".equals(normalized) || "ERROR".equals(normalized))
                .build();
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}

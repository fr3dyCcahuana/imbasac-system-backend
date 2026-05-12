package com.paulfernandosr.possystembackend.salev2.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.documentseries.DocumentSeriesPolicy;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.LockedDocumentSeries;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.EmitContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.PreviewContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.SaveContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ContractSunatDraftRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.DocumentSeriesRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2SunatRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftEmissionResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftPreviewResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftSaveRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.SaleV2SunatMapper;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractSunatDraftService implements
        GetContractSunatDraftUseCase,
        SaveContractSunatDraftUseCase,
        PreviewContractSunatDraftUseCase,
        EmitContractSunatDraftUseCase {

    private static final String SUCCESS_RESPONSE = "0";

    private final ContractSunatDraftRepository repository;
    private final UserRepository userRepository;
    private final DocumentSeriesPolicy documentSeriesPolicy;
    private final DocumentSeriesRepository documentSeriesRepository;

    private final RestClient sunatRestClient;
    private final SunatProps sunatProps;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ContractSunatDraftResponse getOrCreate(Long saleId, String username) {
        validateSaleId(saleId);

        User user = findUser(username);

        ContractSunatDraftResponse current = repository.findBySaleId(saleId);
        if (current != null) {
            return current;
        }

        return repository.createFromSale(saleId, user.getId());
    }

    @Override
    @Transactional
    public ContractSunatDraftResponse save(Long saleId, ContractSunatDraftSaveRequest request, String username) {
        validateSaleId(saleId);
        validateSaveRequest(request);

        User user = findUser(username);

        documentSeriesPolicy.requireAllowed(
                request.getDocType().trim().toUpperCase(),
                request.getSeries().trim().toUpperCase(),
                InvalidSaleV2Exception::new
        );

        return repository.saveDraft(saleId, request, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ContractSunatDraftPreviewResponse preview(Long saleId, ContractSunatDraftSaveRequest request) {
        validateSaleId(saleId);
        validateSaveRequest(request);

        ContractSunatDraftResponse current = repository.findBySaleId(saleId);
        if (current == null) {
            throw new InvalidSaleV2Exception("Primero debes crear el borrador SUNAT.");
        }

        ContractSunatDraftResponse calculated = calculatePreview(current, request);

        ContractSunatDraftPreviewResponse preview = new ContractSunatDraftPreviewResponse();
        copy(calculated, preview);
        preview.setWarnings(buildWarnings(calculated));

        return preview;
    }

    @Override
    @Transactional
    public ContractSunatDraftEmissionResponse emit(Long saleId, String username) {
        validateSaleId(saleId);
        findUser(username);

        ContractSunatDraftResponse draft = repository.lockDraftForEmission(saleId);

        if (draft == null) {
            throw new InvalidSaleV2Exception("No existe borrador SUNAT para esta venta.");
        }

        if (!"BORRADOR".equalsIgnoreCase(nz(draft.getStatus()))) {
            throw new InvalidSaleV2Exception("El borrador SUNAT no está editable. Estado: " + draft.getStatus());
        }

        if ("ACEPTADO".equalsIgnoreCase(nz(draft.getSunatStatus()))) {
            throw new InvalidSaleV2Exception("Esta venta ya fue aceptada por SUNAT.");
        }

        documentSeriesPolicy.requireAllowed(
                draft.getDocType(),
                draft.getSeries(),
                InvalidSaleV2Exception::new
        );

        LockedDocumentSeries lockedSeries = null;
        Long sunatNumber = draft.getNumber();

        /*
         * En contratos la numeración normalmente ya fue reservada cuando se generó
         * la venta BOLETA/FACTURA. Solo reservamos aquí si el borrador antiguo no
         * tiene número por compatibilidad con registros previos.
         */
        if (sunatNumber == null) {
            lockedSeries = documentSeriesRepository.lockSeries(
                    draft.getDocType(),
                    draft.getSeries()
            );

            sunatNumber = lockedSeries.getNextNumber();
            repository.setDraftNumberAndStatus(draft.getId(), sunatNumber, "BORRADOR");
            draft.setNumber(sunatNumber);
        }

        List<ContractSunatDraftRepository.DraftItemForSunat> draftItems =
                repository.findDraftItemsForSunat(draft.getId());

        if (draftItems.isEmpty()) {
            throw new InvalidSaleV2Exception("El borrador SUNAT no tiene items.");
        }

        SaleV2SunatRepository.LockedSunatSale saleForSunat = buildSaleForSunat(draft);

        List<SaleV2SunatRepository.SaleItemForSunat> itemsForSunat = draftItems.stream()
                .map(this::toSaleItemForSunat)
                .toList();

        DocumentRequest sunatRequest = SaleV2SunatMapper.map(
                sunatProps,
                saleForSunat,
                itemsForSunat
        );

        log.info("SUNAT contract draft request: {}", sunatRequest);

        try {
            String rawResponse = sunatRestClient.post()
                    .body(sunatRequest)
                    .retrieve()
                    .body(String.class);

            log.info("SUNAT contract draft response: {}", rawResponse);

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode data = root != null ? root.path("data") : null;

            String providerError = textValue(data, "error");

            if (providerError != null && !providerError.isBlank()) {
                LocalDateTime emittedAt = LocalDateTime.now();

                repository.markSaleEmissionResult(
                        saleId,
                        "ERROR",
                        null,
                        providerError,
                        null,
                        null,
                        null,
                        null,
                        emittedAt
                );

                return buildEmissionResponse(
                        draft,
                        "ERROR",
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
            String description = defaultIfBlank(
                    textValue(data, "respuesta_sunat_descripcion"),
                    "Respuesta vacía de SUNAT"
            );

            String hashCode = extractHashCode(data != null ? data.path("codigo_hash") : null);
            String xmlPath = textValue(data, "ruta_xml");
            String cdrPath = textValue(data, "ruta_cdr");
            String pdfPath = textValue(data, "ruta_pdf");

            String finalStatus = SUCCESS_RESPONSE.equals(code) ? "ACEPTADO" : "RECHAZADO";
            LocalDateTime emittedAt = LocalDateTime.now();

            repository.markSaleEmissionResult(
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

            if ("ACEPTADO".equals(finalStatus)) {
                repository.setDraftNumberAndStatus(draft.getId(), sunatNumber, "EMITIDO");
                if (lockedSeries != null) {
                    documentSeriesRepository.incrementNextNumber(lockedSeries.getId());
                }
            }

            return buildEmissionResponse(
                    draft,
                    finalStatus,
                    code,
                    description,
                    hashCode,
                    xmlPath,
                    cdrPath,
                    pdfPath,
                    emittedAt
            );

        } catch (RuntimeException ex) {
            LocalDateTime emittedAt = LocalDateTime.now();
            repository.markSaleEmissionError(saleId, ex.getMessage(), emittedAt);
            throw ex;
        } catch (Exception ex) {
            LocalDateTime emittedAt = LocalDateTime.now();
            repository.markSaleEmissionError(saleId, ex.getMessage(), emittedAt);
            throw new InvalidSaleV2Exception("No se pudo interpretar la respuesta de SUNAT: " + ex.getMessage());
        }
    }

    private SaleV2SunatRepository.LockedSunatSale buildSaleForSunat(ContractSunatDraftResponse draft) {
        return SaleV2SunatRepository.LockedSunatSale.builder()
                .saleId(draft.getSaleId())
                .status("EMITIDA")
                .docType(draft.getDocType())
                .series(draft.getSeries())
                .number(draft.getNumber())
                .issueDate(draft.getIssueDate())
                .createdAt(LocalDateTime.now())
                .currency("PEN")
                .customerDocType(draft.getCustomerDocType())
                .customerDocNumber(draft.getCustomerDocNumber())
                .customerName(draft.getCustomerName())
                .customerAddress(draft.getCustomerAddress())
                .taxStatus(draft.getTaxStatus())
                .subtotal(nz(draft.getSubtotal()))
                .discountTotal(nz(draft.getDiscountTotal()))
                .igvAmount(nz(draft.getIgvAmount()))
                .total(nz(draft.getTotal()))
                .paymentType("CONTADO")
                .notes("COMPROBANTE ELECTRÓNICO EMITIDO DESDE CONTRATO #" + draft.getContractId())
                .sunatStatus(draft.getSunatStatus())
                .build();
    }

    private SaleV2SunatRepository.SaleItemForSunat toSaleItemForSunat(
            ContractSunatDraftRepository.DraftItemForSunat item
    ) {
        return SaleV2SunatRepository.SaleItemForSunat.builder()
                .lineNumber(item.getLineNumber())
                .productId(item.getProductId())
                .sku(item.getSku())
                .description(item.getDescription())
                .productCategory(item.getProductCategory())
                .quantity(item.getQuantity())
                .revenueTotal(item.getRevenueTotal())
                .lineKind("VENDIDO")
                .visibleInDocument(true)
                .serialUnitId(item.getSerialUnitId())
                .vin(item.getVin())
                .chassisNumber(item.getChassisNumber())
                .engineNumber(item.getEngineNumber())
                .color(item.getColor())
                .yearMake(item.getYearMake())
                .duaNumber(item.getDuaNumber())
                .duaItem(item.getDuaItem())
                .brand(item.getBrand())
                .model(item.getModel())
                .vehicleType(item.getVehicleType())
                .bodywork(item.getBodywork())
                .engineCapacity(item.getEngineCapacity())
                .fuel(item.getFuel())
                .cylinders(item.getCylinders())
                .netWeight(item.getNetWeight())
                .payload(item.getPayload())
                .grossWeight(item.getGrossWeight())
                .vehicleClass(item.getVehicleClass())
                .enginePower(item.getEnginePower())
                .rollingForm(item.getRollingForm())
                .seats(item.getSeats())
                .passengers(item.getPassengers())
                .axles(item.getAxles())
                .wheels(item.getWheels())
                .length(item.getLength())
                .width(item.getWidth())
                .height(item.getHeight())
                .build();
    }

    private ContractSunatDraftResponse calculatePreview(
            ContractSunatDraftResponse current,
            ContractSunatDraftSaveRequest request
    ) {
        ContractSunatDraftResponse calculated = new ContractSunatDraftResponse();
        copy(current, calculated);

        calculated.setDocType(request.getDocType());
        calculated.setSeries(request.getSeries());
        calculated.setIssueDate(request.getIssueDate());
        calculated.setTaxStatus(request.getTaxStatus());
        calculated.setTaxReason("NO_GRAVADA".equalsIgnoreCase(request.getTaxStatus()) ? "20" : null);
        calculated.setIgvRate(request.getIgvRate() != null ? request.getIgvRate() : new BigDecimal("18.00"));
        calculated.setIgvIncluded(Boolean.TRUE.equals(request.getIgvIncluded()));

        if (current.getItems() != null) {
            calculated.setItems(current.getItems().stream()
                    .map(item -> {
                        var clone = com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftItemResponse.builder()
                                .id(item.getId())
                                .saleItemId(item.getSaleItemId())
                                .lineNumber(item.getLineNumber())
                                .productId(item.getProductId())
                                .sku(item.getSku())
                                .description(item.getDescription())
                                .quantity(item.getQuantity())
                                .originalUnitPrice(item.getOriginalUnitPrice())
                                .sunatUnitPrice(item.getSunatUnitPrice())
                                .originalRevenueTotal(item.getOriginalRevenueTotal())
                                .sunatRevenueTotal(item.getSunatRevenueTotal())
                                .build();

                        if (request.getItems() != null) {
                            request.getItems().stream()
                                    .filter(x -> x.getSaleItemId() != null && x.getSaleItemId().equals(item.getSaleItemId()))
                                    .findFirst()
                                    .ifPresent(x -> {
                                        clone.setSunatUnitPrice(x.getSunatUnitPrice());
                                        clone.setSunatRevenueTotal(item.getQuantity().multiply(x.getSunatUnitPrice()));
                                    });
                        }

                        return clone;
                    })
                    .toList());
        }

        BigDecimal gross = calculated.getItems() == null
                ? BigDecimal.ZERO
                : calculated.getItems().stream()
                .map(i -> i.getSunatRevenueTotal() == null ? BigDecimal.ZERO : i.getSunatRevenueTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rate = calculated.getIgvRate().divide(new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP);

        if (!"GRAVADA".equalsIgnoreCase(calculated.getTaxStatus())) {
            calculated.setSubtotal(gross);
            calculated.setIgvAmount(BigDecimal.ZERO);
            calculated.setTotal(gross);
        } else if (Boolean.TRUE.equals(calculated.getIgvIncluded())) {
            BigDecimal subtotal = gross.divide(BigDecimal.ONE.add(rate), 4, java.math.RoundingMode.HALF_UP);
            calculated.setSubtotal(subtotal);
            calculated.setIgvAmount(gross.subtract(subtotal));
            calculated.setTotal(gross);
        } else {
            BigDecimal igv = gross.multiply(rate);
            calculated.setSubtotal(gross);
            calculated.setIgvAmount(igv);
            calculated.setTotal(gross.add(igv));
        }

        calculated.setDiscountTotal(BigDecimal.ZERO);

        return calculated;
    }

    private List<String> buildWarnings(ContractSunatDraftResponse draft) {
        List<String> warnings = new ArrayList<>();

        if ("FACTURA".equalsIgnoreCase(draft.getDocType())
                && !"RUC".equalsIgnoreCase(nz(draft.getCustomerDocType()))) {
            warnings.add("FACTURA requiere cliente con RUC.");
        }

        return warnings;
    }

    private ContractSunatDraftEmissionResponse buildEmissionResponse(
            ContractSunatDraftResponse draft,
            String status,
            String code,
            String description,
            String hashCode,
            String xmlPath,
            String cdrPath,
            String pdfPath,
            LocalDateTime emittedAt
    ) {
        return ContractSunatDraftEmissionResponse.builder()
                .saleId(draft.getSaleId())
                .contractId(draft.getContractId())
                .docType(draft.getDocType())
                .series(draft.getSeries())
                .number(draft.getNumber())
                .sunatStatus(status)
                .sunatCode(code)
                .sunatDescription(description)
                .hashCode(hashCode)
                .xmlPath(xmlPath)
                .cdrPath(cdrPath)
                .pdfPath(pdfPath)
                .emittedAt(emittedAt)
                .build();
    }

    private void validateSaveRequest(ContractSunatDraftSaveRequest request) {
        if (request == null) {
            throw new InvalidSaleV2Exception("Request obligatorio.");
        }

        if (request.getDocType() == null || request.getDocType().isBlank()) {
            throw new InvalidSaleV2Exception("docType es obligatorio.");
        }

        if (request.getSeries() == null || request.getSeries().isBlank()) {
            throw new InvalidSaleV2Exception("series es obligatorio.");
        }

        if (request.getIssueDate() == null) {
            throw new InvalidSaleV2Exception("issueDate es obligatorio.");
        }

        if (request.getTaxStatus() == null || request.getTaxStatus().isBlank()) {
            throw new InvalidSaleV2Exception("taxStatus es obligatorio.");
        }

        if (request.getEditReason() == null || request.getEditReason().trim().length() < 5) {
            throw new InvalidSaleV2Exception("editReason es obligatorio y debe tener al menos 5 caracteres.");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidSaleV2Exception("items es obligatorio.");
        }

        for (ContractSunatDraftSaveRequest.Item item : request.getItems()) {
            if (item.getSaleItemId() == null) {
                throw new InvalidSaleV2Exception("saleItemId es obligatorio en cada item.");
            }

            if (item.getSunatUnitPrice() == null || item.getSunatUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSaleV2Exception("sunatUnitPrice no puede ser nulo ni negativo.");
            }
        }
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));
    }

    private void validateSaleId(Long saleId) {
        if (saleId == null) {
            throw new InvalidSaleV2Exception("saleId es obligatorio.");
        }
    }

    private void copy(ContractSunatDraftResponse source, ContractSunatDraftResponse target) {
        target.setId(source.getId());
        target.setSaleId(source.getSaleId());
        target.setContractId(source.getContractId());
        target.setDocType(source.getDocType());
        target.setSeries(source.getSeries());
        target.setNumber(source.getNumber());
        target.setIssueDate(source.getIssueDate());
        target.setCustomerDocType(source.getCustomerDocType());
        target.setCustomerDocNumber(source.getCustomerDocNumber());
        target.setCustomerName(source.getCustomerName());
        target.setCustomerAddress(source.getCustomerAddress());
        target.setTaxStatus(source.getTaxStatus());
        target.setTaxReason(source.getTaxReason());
        target.setIgvRate(source.getIgvRate());
        target.setIgvIncluded(source.getIgvIncluded());
        target.setSubtotal(source.getSubtotal());
        target.setDiscountTotal(source.getDiscountTotal());
        target.setIgvAmount(source.getIgvAmount());
        target.setTotal(source.getTotal());
        target.setStatus(source.getStatus());
        target.setEditReason(source.getEditReason());
        target.setSunatStatus(source.getSunatStatus());
        target.setItems(source.getItems());
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) return null;
        return child.asText();
    }

    private String extractHashCode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;

        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    String value = item.asText();
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
            return null;
        }

        if (node.isObject()) {
            JsonNode codeNode = node.path("codigo");
            if (!codeNode.isMissingNode() && !codeNode.isNull()) return codeNode.asText();

            JsonNode hashNode = node.path("hash");
            if (!hashNode.isMissingNode() && !hashNode.isNull()) return hashNode.asText();

            return node.toString();
        }

        String value = node.asText();
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

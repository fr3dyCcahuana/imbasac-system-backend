package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.common.infrastructure.documentseries.DocumentSeriesPolicy;
import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.CostPolicy;
import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.model.LockedDocumentSeries;
import com.paulfernandosr.possystembackend.salev2.domain.model.SaleV2TaxSupport;
import com.paulfernandosr.possystembackend.salev2.domain.model.StockMovementBalance;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.EmitContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.PreviewContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.SaveContractSunatDraftUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ContractSunatDraftRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.DocumentSeriesRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductCostRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockMovementRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SalePaymentRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleV2SunatRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftEmissionResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftItemResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftPreviewResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftSaveRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.SaleV2SunatMapper;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatEmissionResult;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatEmissionResultParser;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private final ContractSunatDraftRepository repository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final DocumentSeriesPolicy documentSeriesPolicy;
    private final DocumentSeriesRepository documentSeriesRepository;
    private final ProductCostRepository productCostRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final SalePaymentRepository salePaymentRepository;

    private final RestClient sunatRestClient;
    private final SunatProps sunatProps;
    private final SunatEmissionResultParser sunatEmissionResultParser;
    private static final ZoneId EMISSION_ZONE = ZoneId.of("America/Lima");

    @Override
    @Transactional
    public ContractSunatDraftResponse getOrCreate(Long contractId, String username) {
        validateContractId(contractId);
        User user = findUser(username);

        ContractSunatDraftResponse current = repository.findByContractId(contractId);
        if (current != null) return current;

        validateContractCanPrepareSunat(contractId);
        return repository.createFromContract(contractId, user.getId());
    }

    @Override
    @Transactional
    public ContractSunatDraftResponse save(Long contractId, ContractSunatDraftSaveRequest request, String username) {
        validateContractId(contractId);

        var contract = contractRepository.findById(contractId);
        if (contract == null) {
            throw new InvalidSaleV2Exception("Contrato no existe: " + contractId);
        }

        validateSaveRequest(request, contract.getPaymentType());

        User user = findUser(username);
        ContractSunatDraftResponse current = repository.findByContractId(contractId);
        if (current == null) {
            validateContractCanPrepareSunat(contractId);
        }

        documentSeriesPolicy.requireAllowed(
                request.getDocType().trim().toUpperCase(),
                request.getSeries().trim().toUpperCase(),
                InvalidSaleV2Exception::new
        );

        return repository.saveDraft(contractId, request, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ContractSunatDraftPreviewResponse preview(Long contractId, ContractSunatDraftSaveRequest request) {
        validateContractId(contractId);

        var contract = contractRepository.findById(contractId);
        if (contract == null) {
            throw new InvalidSaleV2Exception("Contrato no existe: " + contractId);
        }

        validateSaveRequest(request, contract.getPaymentType());

        ContractSunatDraftResponse current = repository.findByContractId(contractId);
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
    @Transactional(noRollbackFor = Exception.class)
    public ContractSunatDraftEmissionResponse emit(Long contractId, String username) {
        validateContractId(contractId);
        User user = findUser(username);

        var contract = contractRepository.lockById(contractId);
        if (contract == null) {
            throw new InvalidSaleV2Exception("Contrato no existe: " + contractId);
        }

        validateContractStatusForEmission(contract.getStatus());

        ContractSunatDraftResponse draft = repository.lockDraftForEmission(contractId);
        if (draft == null) {
            draft = repository.createFromContract(contractId, user.getId());
            draft = repository.lockDraftForEmission(contractId);
        }

        if (draft == null) {
            throw new InvalidSaleV2Exception("No existe borrador SUNAT para este contrato.");
        }

        if ("ACEPTADO".equalsIgnoreCase(nz(draft.getSunatStatus()))) {
            throw new InvalidSaleV2Exception("Este contrato ya fue aceptado por SUNAT.");
        }

        if (!"BORRADOR".equalsIgnoreCase(nz(draft.getStatus()))
                && !"EMITIENDO".equalsIgnoreCase(nz(draft.getStatus()))) {
            throw new InvalidSaleV2Exception("El borrador SUNAT no está disponible para emisión. Estado: " + draft.getStatus());
        }

        documentSeriesPolicy.requireAllowed(draft.getDocType(), draft.getSeries(), InvalidSaleV2Exception::new);

        validateDraftReadyForEmission(draft, contract.getPaymentType());

        Long sunatNumber = draft.getNumber();
        if (sunatNumber == null) {
            LockedDocumentSeries lockedSeries = documentSeriesRepository.lockSeries(draft.getDocType(), draft.getSeries());
            sunatNumber = lockedSeries.getNextNumber();
            repository.setDraftNumberAndStatus(draft.getId(), sunatNumber, "EMITIENDO");
            documentSeriesRepository.incrementNextNumber(lockedSeries.getId());
            draft.setNumber(sunatNumber);
            draft.setStatus("EMITIENDO");
        }

        Long saleId = draft.getSaleId();
        if (saleId == null) {
            saleId = createFinalSaleFromDraft(draft, user);
            repository.linkDraftToSale(draft.getId(), saleId);
            repository.linkContractToSale(contractId, saleId);
            draft.setSaleId(saleId);
        }

        List<ContractSunatDraftRepository.DraftItemForSunat> draftItems = repository.findDraftItemsForSunat(draft.getId());
        if (draftItems.isEmpty()) {
            throw new InvalidSaleV2Exception("El borrador SUNAT no tiene items.");
        }

        SaleV2SunatRepository.LockedSunatSale saleForSunat = buildSaleForSunat(draft);
        List<SaleV2SunatRepository.SaleItemForSunat> itemsForSunat = draftItems.stream()
                .map(this::toSaleItemForSunat)
                .toList();

        DocumentRequest sunatRequest = SaleV2SunatMapper.map(sunatProps, saleForSunat, itemsForSunat);

        log.info("SUNAT contract draft request: saleId={}, contractId={}, docType={}, series={}, number={}",
                draft.getSaleId(), draft.getContractId(), draft.getDocType(), draft.getSeries(), draft.getNumber());

        try {
            String rawResponse = sunatRestClient.post()
                    .body(sunatRequest)
                    .retrieve()
                    .body(String.class);

            log.info("SUNAT contract draft response: {}", rawResponse);

            SunatEmissionResult result = sunatEmissionResultParser.parse(rawResponse, LocalDateTime.now());

            persistEmissionResult(draft, saleId, result);

            if (result.isAccepted()) {
                contractRepository.updateStatusAndSale(contractId, ContractStatus.FACTURADO, saleId, contract.getNotes());
            }

            return buildEmissionResponse(draft, result);
        } catch (Exception ex) {
            SunatEmissionResult result = sunatEmissionResultParser.fromException(ex, LocalDateTime.now());
            persistEmissionResult(draft, saleId, result);
            return buildEmissionResponse(draft, result);
        }
    }

    private Long createFinalSaleFromDraft(ContractSunatDraftResponse draft, User user) {
        List<ContractSunatDraftRepository.DraftItemForSunat> items = repository.findDraftItemsForSunat(draft.getId());
        if (items.isEmpty()) throw new InvalidSaleV2Exception("El borrador SUNAT no tiene items.");

        Long saleId = repository.createSaleFromDraft(
                draft.getId(),
                user.getId(),
                draft.getNumber(),
                "COMPROBANTE ELECTRÓNICO EMITIDO DESDE CONTRATO #" + draft.getContractId()
        );

        for (ContractSunatDraftRepository.DraftItemForSunat item : items) {
            BigDecimal unitCost = productCostRepository.getUnitCost(item.getProductId(), CostPolicy.PROMEDIO);
            unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
            BigDecimal totalCost = unitCost.multiply(nz(item.getQuantity())).setScale(4, java.math.RoundingMode.HALF_UP);

            Long saleItemId = repository.createSaleItemFromDraftLine(saleId, item, unitCost, totalCost);

            if (Boolean.TRUE.equals(item.getAffectsStock())) {
                StockMovementBalance balance = productStockRepository.decreaseOnHandOrFail(item.getProductId(), nz(item.getQuantity()));
                productStockMovementRepository.createOutSale(
                        item.getProductId(),
                        nz(item.getQuantity()),
                        saleItemId,
                        unitCost,
                        totalCost,
                        balance.getQuantityOnHand(),
                        balance.getAverageCost() == null ? unitCost : balance.getAverageCost()
                );
            }

            if (Boolean.TRUE.equals(item.getManageBySerial()) && item.getSerialUnitId() != null) {
                productSerialUnitRepository.markAsSold(item.getSerialUnitId(), saleItemId);
            }
        }

        repository.updateSaleTotalsFromDraft(saleId, draft.getId());
        salePaymentRepository.insert(saleId, defaultIfBlank(draft.getPaymentMethod(), "OTRO"), nz(draft.getTotal()));
        return saleId;
    }

    private void persistEmissionResult(ContractSunatDraftResponse draft, Long saleId, SunatEmissionResult result) {
        String draftStatus = null;
        if (result.isAccepted()) {
            draftStatus = "EMITIDO";
        } else if (result.isRejected()) {
            draftStatus = "BLOQUEADO";
        } else if (result.isCommunicationError()) {
            draftStatus = "EMITIENDO";
        }

        repository.markSaleEmissionResult(
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

        repository.markDraftEmissionResult(
                draft.getId(),
                saleId,
                result.getStatus(),
                result.getCode(),
                result.getDescription(),
                result.getHashCode(),
                result.getXmlPath(),
                result.getCdrPath(),
                result.getPdfPath(),
                result.getEmittedAt(),
                draftStatus
        );
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

    private SaleV2SunatRepository.SaleItemForSunat toSaleItemForSunat(ContractSunatDraftRepository.DraftItemForSunat item) {
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

    private ContractSunatDraftResponse calculatePreview(ContractSunatDraftResponse current, ContractSunatDraftSaveRequest request) {
        ContractSunatDraftResponse calculated = new ContractSunatDraftResponse();
        copy(current, calculated);

        calculated.setDocType(request.getDocType());
        calculated.setSeries(request.getSeries());
        calculated.setIssueDate(request.getIssueDate());
        calculated.setBillingCustomerId(request.getBillingCustomerId());
        calculated.setCustomerDocType(request.getBillingDocType());
        calculated.setCustomerDocNumber(request.getBillingDocNumber());
        calculated.setCustomerName(request.getBillingName());
        calculated.setCustomerAddress(request.getBillingAddress());
        calculated.setCustomerUbigeo(request.getBillingUbigeo());
        calculated.setCustomerDepartment(request.getBillingDepartment());
        calculated.setCustomerProvince(request.getBillingProvince());
        calculated.setCustomerDistrict(request.getBillingDistrict());
        calculated.setPaymentMethod(request.getPaymentMethod());
        calculated.setTaxStatus(request.getTaxStatus());
        calculated.setTaxReason("NO_GRAVADA".equalsIgnoreCase(request.getTaxStatus()) ? "20" : null);
        calculated.setIgvRate(request.getIgvRate() != null ? request.getIgvRate() : new BigDecimal("18.00"));
        calculated.setIgvIncluded(Boolean.TRUE.equals(request.getIgvIncluded()));

        if (current.getItems() != null) {
            calculated.setItems(current.getItems().stream()
                    .map(item -> {
                        ContractSunatDraftItemResponse clone = ContractSunatDraftItemResponse.builder()
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
                                    .filter(x -> x.getLineNumber() != null && x.getLineNumber().equals(item.getLineNumber()))
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
        if ("FACTURA".equalsIgnoreCase(draft.getDocType()) && !"RUC".equalsIgnoreCase(nz(draft.getCustomerDocType()))) {
            warnings.add("FACTURA requiere cliente con RUC.");
        }
        if (draft.getNumber() == null) {
            warnings.add("La serie y número se asignarán recién al emitir SUNAT.");
        }
        return warnings;
    }

    private ContractSunatDraftEmissionResponse buildEmissionResponse(ContractSunatDraftResponse draft, SunatEmissionResult result) {
        return ContractSunatDraftEmissionResponse.builder()
                .saleId(draft.getSaleId())
                .contractId(draft.getContractId())
                .docType(draft.getDocType())
                .series(draft.getSeries())
                .number(draft.getNumber())
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

    private void validateContractCanPrepareSunat(Long contractId) {
        var contract = contractRepository.findById(contractId);
        if (contract == null) throw new InvalidSaleV2Exception("Contrato no existe: " + contractId);
        if (contract.getSaleId() != null) throw new InvalidSaleV2Exception("Contrato ya tiene venta asociada: " + contract.getSaleId());
        validateContractStatusForEmission(contract.getStatus());
    }

    private void validateContractStatusForEmission(ContractStatus status) {
        if (status == ContractStatus.CONFIRMADO || status == ContractStatus.PAGADO_PENDIENTE_SUNAT) return;
        throw new InvalidSaleV2Exception("Estado de contrato no permitido para SUNAT: " + status + ". Contado debe estar CONFIRMADO; crédito debe estar PAGADO_PENDIENTE_SUNAT.");
    }

    private void validateDraftReadyForEmission(ContractSunatDraftResponse draft, Object paymentType) {
        if (draft.getDocType() == null || draft.getDocType().isBlank()) throw new InvalidSaleV2Exception("docType es obligatorio.");
        if (draft.getSeries() == null || draft.getSeries().isBlank()) throw new InvalidSaleV2Exception("series es obligatorio.");
        if (draft.getIssueDate() == null) throw new InvalidSaleV2Exception("issueDate es obligatorio.");
        if (draft.getCustomerDocType() == null || draft.getCustomerDocType().isBlank()) throw new InvalidSaleV2Exception("Cliente SUNAT: tipo de documento obligatorio.");
        if (draft.getCustomerDocNumber() == null || draft.getCustomerDocNumber().isBlank()) throw new InvalidSaleV2Exception("Cliente SUNAT: número de documento obligatorio.");
        if (draft.getCustomerName() == null || draft.getCustomerName().isBlank()) throw new InvalidSaleV2Exception("Cliente SUNAT: nombre/razón social obligatorio.");
        if ("FACTURA".equalsIgnoreCase(draft.getDocType()) && !"RUC".equalsIgnoreCase(draft.getCustomerDocType())) {
            throw new InvalidSaleV2Exception("FACTURA requiere cliente SUNAT con RUC.");
        }

        validateIssueDateAllowedBySunat(draft.getIssueDate());
        SaleV2TaxSupport.validateCustomerDocumentForSunat(
                DocType.valueOf(draft.getDocType().trim().toUpperCase()),
                draft.getCustomerDocType(),
                draft.getCustomerDocNumber(),
                draft.getTotal()
        );

        // Regla de negocio: paymentMethod solo es obligatorio para contratos CONTADO.
        // En CREDITO, las cuotas ya fueron registradas contra el contrato; al emitir SUNAT
        // se generará la venta final y, si no hay método, se registrará internamente como OTRO.
        if (isContado(paymentType) && (draft.getPaymentMethod() == null || draft.getPaymentMethod().isBlank())) {
            throw new InvalidSaleV2Exception("paymentMethod es obligatorio para contratos al contado.");
        }
    }

    private void validateIssueDateAllowedBySunat(LocalDate issueDate) {
        LocalDate today = LocalDate.now(EMISSION_ZONE);
        LocalDate minAllowedDate = today.minusDays(3);

        if (issueDate == null) {
            throw new InvalidSaleV2Exception("La fecha de emision es obligatoria para enviar a SUNAT.");
        }

        if (issueDate.isBefore(minAllowedDate) || issueDate.isAfter(today)) {
            throw new InvalidSaleV2Exception(
                    "La fecha de emision permitida para SUNAT es hoy o maximo 3 dias calendario hacia atras. Fecha venta="
                            + issueDate + ", rango permitido=" + minAllowedDate + " a " + today
            );
        }
    }

    private void validateSaveRequest(ContractSunatDraftSaveRequest request, Object paymentType) {
        if (request == null) throw new InvalidSaleV2Exception("Request obligatorio.");
        if (request.getDocType() == null || request.getDocType().isBlank()) throw new InvalidSaleV2Exception("docType es obligatorio.");
        if (request.getSeries() == null || request.getSeries().isBlank()) throw new InvalidSaleV2Exception("series es obligatorio.");
        if (request.getIssueDate() == null) throw new InvalidSaleV2Exception("issueDate es obligatorio.");
        if (request.getBillingDocType() == null || request.getBillingDocType().isBlank()) throw new InvalidSaleV2Exception("billingDocType es obligatorio.");
        if (request.getBillingDocNumber() == null || request.getBillingDocNumber().isBlank()) throw new InvalidSaleV2Exception("billingDocNumber es obligatorio.");
        if (request.getBillingName() == null || request.getBillingName().isBlank()) throw new InvalidSaleV2Exception("billingName es obligatorio.");
        if (isContado(paymentType) && (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank())) {
            throw new InvalidSaleV2Exception("paymentMethod es obligatorio para contratos al contado.");
        }
        if (request.getTaxStatus() == null || request.getTaxStatus().isBlank()) throw new InvalidSaleV2Exception("taxStatus es obligatorio.");
        if (request.getEditReason() == null || request.getEditReason().trim().length() < 5) throw new InvalidSaleV2Exception("editReason es obligatorio y debe tener al menos 5 caracteres.");
        if (request.getItems() == null || request.getItems().isEmpty()) throw new InvalidSaleV2Exception("items es obligatorio.");

        for (ContractSunatDraftSaveRequest.Item item : request.getItems()) {
            if (item.getLineNumber() == null) throw new InvalidSaleV2Exception("lineNumber es obligatorio en cada item.");
            if (item.getSunatUnitPrice() == null || item.getSunatUnitPrice().compareTo(BigDecimal.ZERO) < 0) throw new InvalidSaleV2Exception("sunatUnitPrice no puede ser nulo ni negativo.");
        }
    }

    private boolean isContado(Object paymentType) {
        return "CONTADO".equalsIgnoreCase(String.valueOf(paymentType));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));
    }

    private void validateContractId(Long contractId) {
        if (contractId == null) throw new InvalidSaleV2Exception("contractId es obligatorio.");
    }

    private void copy(ContractSunatDraftResponse source, ContractSunatDraftResponse target) {
        target.setId(source.getId());
        target.setSaleId(source.getSaleId());
        target.setContractId(source.getContractId());
        target.setDocType(source.getDocType());
        target.setSeries(source.getSeries());
        target.setNumber(source.getNumber());
        target.setIssueDate(source.getIssueDate());
        target.setBillingCustomerId(source.getBillingCustomerId());
        target.setCustomerDocType(source.getCustomerDocType());
        target.setCustomerDocNumber(source.getCustomerDocNumber());
        target.setCustomerName(source.getCustomerName());
        target.setCustomerAddress(source.getCustomerAddress());
        target.setCustomerUbigeo(source.getCustomerUbigeo());
        target.setCustomerDepartment(source.getCustomerDepartment());
        target.setCustomerProvince(source.getCustomerProvince());
        target.setCustomerDistrict(source.getCustomerDistrict());
        target.setPaymentMethod(source.getPaymentMethod());
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

    private String nz(String value) { return value == null ? "" : value; }
    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String defaultIfBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}

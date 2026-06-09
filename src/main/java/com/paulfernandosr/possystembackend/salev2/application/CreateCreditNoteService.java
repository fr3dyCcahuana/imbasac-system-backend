package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.common.infrastructure.documentseries.DocumentSeriesPolicy;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.model.LockedDocumentSeries;
import com.paulfernandosr.possystembackend.salev2.domain.model.StockMovementBalance;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateCreditNoteUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.DocumentSeriesRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockMovementRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.ProductStockRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleCreditNoteRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteCreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteItemResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.CreditNoteSunatMapper;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatCodeInferer;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatEmissionResult;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat.SunatEmissionResultParser;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateCreditNoteService implements CreateCreditNoteUseCase {

    private static final Set<String> ALLOWED_TYPE_CODES = Set.of("01", "06", "07", "09");
    private static final String DOC_TYPE_CREDIT_NOTE_INVOICE = "NOTA_CREDITO_FACTURA";
    private static final String DOC_TYPE_CREDIT_NOTE_RECEIPT = "NOTA_CREDITO_BOLETA";
    private static final String MOTORCYCLE_SUNAT_CODE = "25101801";

    private final SaleCreditNoteRepository creditNoteRepository;
    private final UserRepository userRepository;
    private final DocumentSeriesRepository documentSeriesRepository;
    private final DocumentSeriesPolicy documentSeriesPolicy;
    private final ProductStockRepository productStockRepository;
    private final ProductStockMovementRepository productStockMovementRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final RestClient sunatRestClient;
    private final SunatProps sunatProps;
    private final SunatEmissionResultParser sunatEmissionResultParser;

    @Override
    @Transactional
    public CreditNoteResponse create(Long saleId, CreditNoteCreateRequest request, String username) {
        if (saleId == null) {
            throw new InvalidSaleV2Exception("saleId es obligatorio.");
        }
        if (request == null) {
            throw new InvalidSaleV2Exception("Request vacio.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario invalido: " + username));

        SaleCreditNoteRepository.LockedSaleForCreditNote sale = creditNoteRepository.lockSale(saleId);
        if (sale == null) {
            throw new InvalidSaleV2Exception("Venta no encontrada: " + saleId);
        }

        validateSale(sale);

        String typeCode = normalizeTypeCode(request.getTypeCode());
        String typeDescription = creditNoteTypeDescription(typeCode);
        String reason = normalizeReason(request.getReason(), typeDescription);
        boolean returnToStock = Boolean.TRUE.equals(request.getReturnToStock());

        if ("09".equals(typeCode) && returnToStock) {
            throw new InvalidSaleV2Exception("El motivo 09 disminuye valor y no debe liberar stock.");
        }

        List<SaleCreditNoteRepository.SaleItemForCreditNote> saleItems = creditNoteRepository.findSaleItems(saleId);
        if (saleItems.isEmpty()) {
            throw new InvalidSaleV2Exception("La venta no tiene items para nota de credito.");
        }

        Map<Long, SaleCreditNoteRepository.SaleItemForCreditNote> itemById = saleItems.stream()
                .filter(i -> Boolean.TRUE.equals(i.getVisibleInDocument()))
                .filter(i -> "VENDIDO".equalsIgnoreCase(blankIfNull(i.getLineKind())))
                .collect(Collectors.toMap(
                        SaleCreditNoteRepository.SaleItemForCreditNote::getSaleItemId,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        if (itemById.isEmpty()) {
            throw new InvalidSaleV2Exception("La venta no tiene lineas vendidas visibles para SUNAT.");
        }

        Map<Long, BigDecimal> alreadyCredited = creditNoteRepository.findCreditedQuantities(saleId);
        List<ComputedCreditLine> lines = buildLines(typeCode, request.getItems(), itemById, alreadyCredited, returnToStock);
        if (lines.isEmpty()) {
            throw new InvalidSaleV2Exception("Debe seleccionar al menos una linea con cantidad mayor a cero.");
        }

        Totals totals = calculateTotals(sale, lines);
        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();
        String seriesDocType = creditNoteSeriesDocType(sale.getDocType());
        String series = documentSeriesPolicy.resolveOrDefault(seriesDocType, null, InvalidSaleV2Exception::new);
        LockedDocumentSeries lockedSeries = documentSeriesRepository.lockSeries(seriesDocType, series);

        Long creditNoteId = creditNoteRepository.insertCreditNote(SaleCreditNoteRepository.CreditNoteRecord.builder()
                .saleId(sale.getSaleId())
                .createdBy(user.getId())
                .docType(seriesDocType)
                .series(series)
                .number(lockedSeries.getNextNumber())
                .issueDate(issueDate)
                .currency(sale.getCurrency())
                .customerDocType(sale.getCustomerDocType())
                .customerDocNumber(sale.getCustomerDocNumber())
                .customerName(sale.getCustomerName())
                .customerAddress(sale.getCustomerAddress())
                .taxStatus(sale.getTaxStatus())
                .igvRate(sale.getIgvRate())
                .igvIncluded(Boolean.TRUE.equals(sale.getIgvIncluded()))
                .creditNoteTypeCode(typeCode)
                .creditNoteTypeDescription(typeDescription)
                .reason(reason)
                .returnedToStock(returnToStock)
                .subtotal(totals.subtotal())
                .igvAmount(totals.igvAmount())
                .total(totals.total())
                .build());

        List<CreditNoteItemResponse> itemResponses = new ArrayList<>();
        List<CreditNoteSunatMapper.CreditNoteLineForSunat> sunatLines = new ArrayList<>();
        List<ReversibleCreditNoteLine> reversibleLines = new ArrayList<>();

        int lineNumber = 1;
        for (ComputedCreditLine line : lines) {
            SaleCreditNoteRepository.SaleItemForCreditNote original = line.original();
            BigDecimal unitCost = nz(original.getUnitCostSnapshot());
            BigDecimal totalCost = unitCost.multiply(line.quantity()).setScale(4, RoundingMode.HALF_UP);
            boolean lineReturnsToStock = shouldReturnStock(returnToStock, original);

            Long creditNoteItemId = creditNoteRepository.insertCreditNoteItem(SaleCreditNoteRepository.CreditNoteItemRecord.builder()
                    .creditNoteId(creditNoteId)
                    .saleItemId(original.getSaleItemId())
                    .lineNumber(lineNumber++)
                    .productId(original.getProductId())
                    .sku(original.getSku())
                    .description(original.getDescription())
                    .presentation(original.getPresentation())
                    .factor(original.getFactor())
                    .quantity(line.quantity())
                    .unitPrice(original.getUnitPrice())
                    .discountPercent(original.getDiscountPercent())
                    .discountAmount(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .facturableSunat(original.getFacturableSunat())
                    .affectsStock(original.getAffectsStock())
                    .visibleInDocument(original.getVisibleInDocument())
                    .unitCostSnapshot(unitCost)
                    .totalCostSnapshot(totalCost)
                    .revenueTotal(line.revenueTotal())
                    .returnedToStock(lineReturnsToStock)
                    .build());

            if (lineReturnsToStock) {
                StockMovementBalance balance = productStockRepository.increaseOnHand(original.getProductId(), line.quantity());
                productStockMovementRepository.createInCreditNote(
                        original.getProductId(),
                        line.quantity(),
                        creditNoteItemId,
                        unitCost,
                        totalCost,
                        balance.getQuantityOnHand(),
                        nz(balance.getAverageCost(), unitCost)
                );

                if (original.getSerialUnitId() != null) {
                    productSerialUnitRepository.markAsReturned(original.getSerialUnitId());
                }
            }

            reversibleLines.add(ReversibleCreditNoteLine.builder()
                    .creditNoteItemId(creditNoteItemId)
                    .saleItemId(original.getSaleItemId())
                    .productId(original.getProductId())
                    .quantity(line.quantity())
                    .unitCost(unitCost)
                    .totalCost(totalCost)
                    .returnedToStock(lineReturnsToStock)
                    .serialUnitId(original.getSerialUnitId())
                    .build());

            itemResponses.add(CreditNoteItemResponse.builder()
                    .creditNoteItemId(creditNoteItemId)
                    .saleItemId(original.getSaleItemId())
                    .productId(original.getProductId())
                    .sku(original.getSku())
                    .description(original.getDescription())
                    .quantity(line.quantity())
                    .revenueTotal(line.revenueTotal())
                    .returnedToStock(lineReturnsToStock)
                    .build());

            sunatLines.add(CreditNoteSunatMapper.CreditNoteLineForSunat.builder()
                    .saleItemId(original.getSaleItemId())
                    .sku(original.getSku())
                    .description(original.getDescription())
                    .quantity(line.quantity())
                    .revenueTotal(line.revenueTotal())
                    .sunatCode(resolveSunatCode(original))
                    .build());
        }

        documentSeriesRepository.incrementNextNumber(lockedSeries.getId());

        SunatEmissionResult result = emitToSunat(sale, creditNoteId, series, lockedSeries.getNextNumber(),
                issueDate, typeCode, typeDescription, reason, totals, sunatLines);

        creditNoteRepository.updateEmissionResult(
                creditNoteId,
                result.getStatus(),
                result.getCode(),
                result.getDescription(),
                result.getHashCode(),
                result.getXmlPath(),
                result.getCdrPath(),
                result.getPdfPath(),
                result.getEmittedAt()
        );

        rollbackRejectedCreditNoteIfNeeded(creditNoteId, result, reversibleLines);

        return buildResponse(
                creditNoteId,
                sale.getSaleId(),
                seriesDocType,
                series,
                lockedSeries.getNextNumber(),
                issueDate,
                typeCode,
                typeDescription,
                reason,
                returnToStock,
                totals,
                result,
                itemResponses
        );
    }

    private boolean shouldReturnStock(boolean returnToStock,
                                      SaleCreditNoteRepository.SaleItemForCreditNote item) {
        if (!returnToStock) {
            return false;
        }

        return Boolean.TRUE.equals(item.getAffectsStock())
                || Boolean.TRUE.equals(item.getCounterSaleItemAffectsStock());
    }

    private SunatEmissionResult emitToSunat(SaleCreditNoteRepository.LockedSaleForCreditNote sale,
                                            Long creditNoteId,
                                            String series,
                                            Long number,
                                            LocalDate issueDate,
                                            String typeCode,
                                            String typeDescription,
                                            String reason,
                                            Totals totals,
                                            List<CreditNoteSunatMapper.CreditNoteLineForSunat> sunatLines) {
        DocumentRequest request = CreditNoteSunatMapper.map(
                sunatProps,
                sale,
                CreditNoteSunatMapper.CreditNoteForSunat.builder()
                        .series(series)
                        .number(number)
                        .issueDate(issueDate)
                        .typeCode(typeCode)
                        .typeDescription(typeDescription)
                        .reason(reason)
                        .subtotal(totals.subtotal())
                        .igvAmount(totals.igvAmount())
                        .build(),
                sunatLines
        );

        log.info("SUNAT credit note request: saleId={}, creditNoteId={}, series={}, number={}, total={}",
                sale.getSaleId(), creditNoteId, series, number, totals.total());

        try {
            String rawResponse = sunatRestClient.post()
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("SUNAT credit note response: {}", rawResponse);
            return sunatEmissionResultParser.parse(rawResponse, LocalDateTime.now());
        } catch (Exception ex) {
            return sunatEmissionResultParser.fromException(ex, LocalDateTime.now());
        }
    }

    private void rollbackRejectedCreditNoteIfNeeded(Long creditNoteId,
                                                    SunatEmissionResult result,
                                                    List<ReversibleCreditNoteLine> lines) {
        if (result == null || !isDefinitiveRejectedBySunat(result.getStatus(), result.getCode())) {
            return;
        }

        if (lines != null) {
            for (ReversibleCreditNoteLine line : lines) {
                if (line == null || !Boolean.TRUE.equals(line.getReturnedToStock())) {
                    continue;
                }
                if (productStockMovementRepository.existsOutCreditNoteRejection(line.getCreditNoteItemId())) {
                    continue;
                }

                StockMovementBalance balance = productStockRepository.decreaseOnHandOrFail(line.getProductId(), line.getQuantity());
                productStockMovementRepository.createOutCreditNoteRejection(
                        line.getProductId(),
                        line.getQuantity(),
                        line.getCreditNoteItemId(),
                        nz(line.getUnitCost()),
                        nz(line.getTotalCost()),
                        balance.getQuantityOnHand(),
                        nz(balance.getAverageCost(), nz(line.getUnitCost()))
                );

                if (line.getSerialUnitId() != null) {
                    productSerialUnitRepository.markAsSold(line.getSerialUnitId(), line.getSaleItemId());
                }
            }
        }

        creditNoteRepository.markAsVoided(
                creditNoteId,
                "ANULADA AUTOMATICAMENTE POR RECHAZO DEFINITIVO SUNAT: Codigo "
                        + result.getCode() + " - " + result.getDescription()
        );
    }

    private boolean isDefinitiveRejectedBySunat(String sunatStatus, String sunatCode) {
        String status = blankIfNull(sunatStatus).trim().toUpperCase(Locale.ROOT);
        String code = blankIfNull(sunatCode).trim();
        return "RECHAZADO".equals(status) && !code.isBlank() && !"0".equals(code);
    }

    private List<ComputedCreditLine> buildLines(String typeCode,
                                                List<CreditNoteCreateRequest.Item> requestedItems,
                                                Map<Long, SaleCreditNoteRepository.SaleItemForCreditNote> itemById,
                                                Map<Long, BigDecimal> alreadyCredited,
                                                boolean returnToStock) {
        if ("01".equals(typeCode) || "06".equals(typeCode)) {
            return itemById.values().stream()
                    .map(item -> {
                        BigDecimal remaining = remainingQuantity(item, alreadyCredited);
                        if (remaining.signum() <= 0) {
                            return null;
                        }
                        validateSerialReturn(item, remaining, returnToStock);
                        return computeLine(item, remaining);
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new InvalidSaleV2Exception("Debe seleccionar items para nota de credito parcial.");
        }

        List<ComputedCreditLine> lines = new ArrayList<>();
        for (CreditNoteCreateRequest.Item requested : requestedItems) {
            if (requested == null || requested.getSaleItemId() == null) {
                throw new InvalidSaleV2Exception("Cada item de nota debe enviar saleItemId.");
            }
            SaleCreditNoteRepository.SaleItemForCreditNote original = itemById.get(requested.getSaleItemId());
            if (original == null) {
                throw new InvalidSaleV2Exception("El item no pertenece a la venta o no es visible para SUNAT. saleItemId=" + requested.getSaleItemId());
            }

            BigDecimal quantity = normalizeQuantity(requested.getQuantity(), requested.getSaleItemId());
            BigDecimal remaining = remainingQuantity(original, alreadyCredited);
            if (quantity.compareTo(remaining) > 0) {
                throw new InvalidSaleV2Exception("La cantidad supera lo disponible para nota de credito. saleItemId="
                        + requested.getSaleItemId() + ", disponible=" + remaining);
            }

            validateSerialReturn(original, quantity, returnToStock);
            lines.add(computeLine(original, quantity));
        }

        return lines;
    }

    private void validateSerialReturn(SaleCreditNoteRepository.SaleItemForCreditNote item,
                                      BigDecimal quantity,
                                      boolean returnToStock) {
        if (item.getSerialUnitId() == null) {
            return;
        }
        if (quantity.compareTo(BigDecimal.ONE) != 0) {
            throw new InvalidSaleV2Exception("Una unidad serializada solo puede devolverse con cantidad 1. saleItemId=" + item.getSaleItemId());
        }
        if (!returnToStock) {
            throw new InvalidSaleV2Exception("Una unidad serializada devuelta debe liberar stock. saleItemId=" + item.getSaleItemId());
        }
    }

    private ComputedCreditLine computeLine(SaleCreditNoteRepository.SaleItemForCreditNote item, BigDecimal quantity) {
        BigDecimal originalQty = nz(item.getQuantity());
        if (originalQty.signum() <= 0) {
            throw new InvalidSaleV2Exception("Cantidad original invalida. saleItemId=" + item.getSaleItemId());
        }
        BigDecimal unitRevenue = nz(item.getRevenueTotal()).divide(originalQty, 8, RoundingMode.HALF_UP);
        BigDecimal revenueTotal = unitRevenue.multiply(quantity).setScale(4, RoundingMode.HALF_UP);
        return new ComputedCreditLine(item, quantity, revenueTotal);
    }

    private BigDecimal remainingQuantity(SaleCreditNoteRepository.SaleItemForCreditNote item,
                                         Map<Long, BigDecimal> alreadyCredited) {
        BigDecimal credited = alreadyCredited.getOrDefault(item.getSaleItemId(), BigDecimal.ZERO);
        BigDecimal remaining = nz(item.getQuantity()).subtract(credited).setScale(4, RoundingMode.HALF_UP);
        return remaining.signum() < 0 ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : remaining;
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity, Long saleItemId) {
        BigDecimal value = nz(quantity).setScale(4, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            throw new InvalidSaleV2Exception("Cantidad invalida para nota de credito. saleItemId=" + saleItemId);
        }
        return value;
    }

    private Totals calculateTotals(SaleCreditNoteRepository.LockedSaleForCreditNote sale,
                                   List<ComputedCreditLine> lines) {
        BigDecimal subtotal = lines.stream()
                .map(ComputedCreditLine::revenueTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal igvAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (!"NO_GRAVADA".equalsIgnoreCase(blankIfNull(sale.getTaxStatus()))) {
            BigDecimal rate = nz(sale.getIgvRate(), new BigDecimal("18.00"))
                    .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
            igvAmount = subtotal.multiply(rate).setScale(4, RoundingMode.HALF_UP);
        }

        return new Totals(subtotal, igvAmount, subtotal.add(igvAmount).setScale(4, RoundingMode.HALF_UP));
    }

    private void validateSale(SaleCreditNoteRepository.LockedSaleForCreditNote sale) {
        if (!"EMITIDA".equalsIgnoreCase(blankIfNull(sale.getStatus()))) {
            throw new InvalidSaleV2Exception("Solo se puede emitir nota de credito para ventas EMITIDA.");
        }
        String docType = blankIfNull(sale.getDocType()).trim().toUpperCase(Locale.ROOT);
        if (!"BOLETA".equals(docType) && !"FACTURA".equals(docType)) {
            throw new InvalidSaleV2Exception("La nota de credito solo aplica a BOLETA/FACTURA.");
        }
        if (!"ACEPTADO".equalsIgnoreCase(blankIfNull(sale.getSunatStatus()))) {
            throw new InvalidSaleV2Exception("La venta debe estar ACEPTADA por SUNAT antes de emitir nota de credito.");
        }
    }

    private String normalizeTypeCode(String typeCode) {
        String normalized = blankIfNull(typeCode).trim();
        if (!ALLOWED_TYPE_CODES.contains(normalized)) {
            throw new InvalidSaleV2Exception("Motivo SUNAT de nota de credito no soportado. Use 01, 06, 07 o 09.");
        }
        return normalized;
    }

    private String normalizeReason(String reason, String fallback) {
        String text = blankIfNull(reason).trim();
        if (text.isEmpty()) {
            text = fallback;
        }
        if (text.length() < 5) {
            throw new InvalidSaleV2Exception("El sustento de nota de credito debe tener al menos 5 caracteres.");
        }
        return text;
    }

    private String creditNoteTypeDescription(String typeCode) {
        return switch (typeCode) {
            case "01" -> "Anulacion de la operacion";
            case "06" -> "Devolucion total";
            case "07" -> "Devolucion por item";
            case "09" -> "Disminucion en el valor";
            default -> throw new InvalidSaleV2Exception("Motivo SUNAT de nota de credito no soportado.");
        };
    }

    private String creditNoteSeriesDocType(String saleDocType) {
        String docType = blankIfNull(saleDocType).trim().toUpperCase(Locale.ROOT);
        return switch (docType) {
            case "FACTURA" -> DOC_TYPE_CREDIT_NOTE_INVOICE;
            case "BOLETA" -> DOC_TYPE_CREDIT_NOTE_RECEIPT;
            default -> throw new InvalidSaleV2Exception("La nota de credito solo aplica a BOLETA/FACTURA.");
        };
    }

    private String resolveSunatCode(SaleCreditNoteRepository.SaleItemForCreditNote item) {
        String category = blankIfNull(item.getProductCategory()).trim().toUpperCase(Locale.ROOT);
        if (category.contains("MOTOCIC") || "MOTO".equals(category) || "MOTOCICLETA".equals(category)) {
            return MOTORCYCLE_SUNAT_CODE;
        }
        try {
            return SunatCodeInferer.infer(item.getDescription(), item.getProductCategory());
        } catch (Exception ex) {
            return "01010101";
        }
    }

    private CreditNoteResponse buildResponse(Long creditNoteId,
                                             Long saleId,
                                             String docType,
                                             String series,
                                             Long number,
                                             LocalDate issueDate,
                                             String typeCode,
                                             String typeDescription,
                                             String reason,
                                             boolean returnToStock,
                                             Totals totals,
                                             SunatEmissionResult result,
                                             List<CreditNoteItemResponse> itemResponses) {
        return CreditNoteResponse.builder()
                .creditNoteId(creditNoteId)
                .saleId(saleId)
                .docType(docType)
                .series(series)
                .number(number)
                .issueDate(issueDate)
                .creditNoteTypeCode(typeCode)
                .creditNoteTypeDescription(typeDescription)
                .reason(reason)
                .returnedToStock(returnToStock)
                .subtotal(totals.subtotal())
                .igvAmount(totals.igvAmount())
                .total(totals.total())
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
                .items(itemResponses)
                .build();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal nz(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    @Getter
    @Builder
    private static class ReversibleCreditNoteLine {
        private Long creditNoteItemId;
        private Long saleItemId;
        private Long productId;
        private BigDecimal quantity;
        private BigDecimal unitCost;
        private BigDecimal totalCost;
        private Boolean returnedToStock;
        private Long serialUnitId;
    }

    private record ComputedCreditLine(SaleCreditNoteRepository.SaleItemForCreditNote original,
                                      BigDecimal quantity,
                                      BigDecimal revenueTotal) {
    }

    private record Totals(BigDecimal subtotal, BigDecimal igvAmount, BigDecimal total) {
    }
}

package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.CreateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.DocumentSeriesRepositoryV2;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProductSnapshotRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.CreateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.LockedDocumentSeries;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.ProductSnapshot;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateProformaV2Service implements CreateProformaV2UseCase {

    private final DocumentSeriesRepositoryV2 documentSeriesRepository;
    private final ProformaRepository proformaRepository;
    private final ProformaItemRepository proformaItemRepository;
    private final ProductSnapshotRepository productSnapshotRepository;

    @Override
    @Transactional
    public ProformaV2Response create(CreateProformaV2Request request) {
        validateRequest(request);

        LockedDocumentSeries series = documentSeriesRepository.lock(
                request.getStationId(),
                "PROFORMA",
                request.getSeries()
        );

        long number = series.getNextNumber();
        documentSeriesRepository.incrementNextNumber(series.getId());

        LocalDate issueDate = (request.getIssueDate() == null || request.getIssueDate().isBlank())
                ? LocalDate.now()
                : LocalDate.parse(request.getIssueDate());

        // --- Config tributaria (persistida para impresión y coherencia convert->sale) ---
        TaxStatus taxStatus = request.getTaxStatus() != null ? request.getTaxStatus() : TaxStatus.NO_GRAVADA;
        BigDecimal igvRate = request.getIgvRate() != null ? request.getIgvRate() : new BigDecimal("18.00");

        boolean igvIncluded = Boolean.TRUE.equals(request.getIgvIncluded());
        if (taxStatus != TaxStatus.GRAVADA) {
            igvIncluded = false;
        }

        List<ProformaItem> items = new ArrayList<>();

        // Totales documento:
        BigDecimal subtotalBase = BigDecimal.ZERO;     // BASE imponible
        BigDecimal discountTotal = BigDecimal.ZERO;   // descuento aplicado (sobre gross si igvIncluded=true)
        BigDecimal igvAmount = BigDecimal.ZERO;       // IGV total
        BigDecimal total = BigDecimal.ZERO;           // TOTAL final

        int line = 1;
        for (CreateProformaV2Request.Item reqItem : request.getItems()) {

            ProductSnapshot p = productSnapshotRepository.getById(reqItem.getProductId());
            if (p == null) {
                throw new InvalidProformaV2Exception("Producto no encontrado: " + reqItem.getProductId());
            }

            // ✅ Guardrail: solo MOTOR/MOTOCICLETAS pueden ser manage_by_serial=true
            if (Boolean.TRUE.equals(p.getManageBySerial())) {
                String cat = p.getCategory();
                if (cat == null || (!cat.equalsIgnoreCase("MOTOR") && !cat.equalsIgnoreCase("MOTOCICLETAS"))) {
                    throw new InvalidProformaV2Exception(
                            "Producto inválido: manage_by_serial=true solo aplica a categoría MOTOR/MOTOCICLETAS. " +
                                    "SKU=" + p.getSku() + ", category=" + cat
                    );
                }
            }

            BigDecimal qty = new BigDecimal(reqItem.getQuantity());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidProformaV2Exception("Cantidad inválida en línea " + line);
            }

            BigDecimal unitPrice = resolvePriceByList(p, request.getPriceList());
            if (unitPrice == null) {
                throw new InvalidProformaV2Exception("El producto " + p.getSku() + " no tiene precio para lista " + request.getPriceList());
            }

            BigDecimal discountPercent = parseOrZero(reqItem.getDiscountPercent());
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidProformaV2Exception("Descuento inválido en línea " + line);
            }

            // gross: depende de la semántica del precio
            // - si igvIncluded=true y GRAVADA, unitPrice es FINAL (incluye IGV)
            // - si igvIncluded=false (o NO_GRAVADA), unitPrice es BASE (sin IGV)
            BigDecimal gross = qty.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);

            // descuento: sobre el "precio mostrado"
            // - si igvIncluded=true, descuento sobre precio final con IGV (cumple tu regla)
            // - si igvIncluded=false, descuento sobre base
            BigDecimal discountAmount = gross.multiply(discountPercent)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

            BigDecimal grossAfterDiscount = gross.subtract(discountAmount).setScale(4, RoundingMode.HALF_UP);

            BigDecimal baseLine;
            BigDecimal igvLine;

            if (taxStatus == TaxStatus.GRAVADA && igvIncluded) {
                // extraer base e IGV desde precio final
                BigDecimal divisor = BigDecimal.ONE.add(
                        igvRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                ); // 1.18 si rate=18

                baseLine = grossAfterDiscount.divide(divisor, 4, RoundingMode.HALF_UP);
                igvLine = grossAfterDiscount.subtract(baseLine).setScale(4, RoundingMode.HALF_UP);

                subtotalBase = subtotalBase.add(baseLine);
                igvAmount = igvAmount.add(igvLine);
                total = total.add(grossAfterDiscount);
            } else if (taxStatus == TaxStatus.GRAVADA) {
                // precio sin IGV: grossAfterDiscount es BASE
                baseLine = grossAfterDiscount;
                igvLine = BigDecimal.ZERO; // se calcula a nivel documento al final

                subtotalBase = subtotalBase.add(baseLine);
            } else {
                // NO_GRAVADA: no hay IGV
                baseLine = grossAfterDiscount;
                igvLine = BigDecimal.ZERO;

                subtotalBase = subtotalBase.add(baseLine);
            }

            discountTotal = discountTotal.add(discountAmount);

            // Persistimos unitPrice y lineSubtotal con la MISMA semántica:
            // - si igvIncluded=true => unitPrice y lineSubtotal son "gross" (incluye IGV)
            // - si igvIncluded=false => unitPrice y lineSubtotal son "base"
            ProformaItem item = ProformaItem.builder()
                    .proformaId(null) // se setea luego
                    .lineNumber(line)
                    .productId(p.getId())
                    .sku(p.getSku())
                    .description((reqItem.getDescription() != null && !reqItem.getDescription().isBlank()) ? reqItem.getDescription() : p.getName())
                    .presentation((reqItem.getPresentation() != null && !reqItem.getPresentation().isBlank()) ? reqItem.getPresentation() : p.getPresentation())
                    .factor((reqItem.getFactor() != null && !reqItem.getFactor().isBlank()) ? new BigDecimal(reqItem.getFactor()) : p.getFactor())
                    .quantity(qty)
                    .unitPrice(money2(unitPrice))                      // snapshot (2 decimales)
                    .discountPercent(discountPercent.setScale(4, RoundingMode.HALF_UP))
                    .discountAmount(money2(discountAmount))            // 2 decimales
                    .lineSubtotal(money2(grossAfterDiscount))          // 2 decimales, semántica = gross/base según igvIncluded
                    .facturableSunat(p.getFacturableSunat())
                    .affectsStock(p.getAffectsStock())
                    .build();

            items.add(item);
            line++;
        }

        // Normalizar totales
        subtotalBase = subtotalBase.setScale(4, RoundingMode.HALF_UP);
        discountTotal = discountTotal.setScale(4, RoundingMode.HALF_UP);
        igvAmount = igvAmount.setScale(4, RoundingMode.HALF_UP);
        total = total.setScale(4, RoundingMode.HALF_UP);

        // Si es GRAVADA y NO incluye IGV, calcula IGV sobre subtotalBase
        if (taxStatus == TaxStatus.GRAVADA && !igvIncluded) {
            igvAmount = subtotalBase.multiply(igvRate)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            total = subtotalBase.add(igvAmount).setScale(4, RoundingMode.HALF_UP);
        }

        // Si NO_GRAVADA, igvAmount y total
        if (taxStatus != TaxStatus.GRAVADA) {
            igvAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            total = subtotalBase.setScale(4, RoundingMode.HALF_UP);
        }

        Proforma proforma = Proforma.builder()
                .stationId(request.getStationId())
                .createdBy(request.getCreatedBy())
                .series(request.getSeries())
                .number(number)
                .issueDate(issueDate)
                .priceList(request.getPriceList())
                .currency((request.getCurrency() == null || request.getCurrency().isBlank()) ? "PEN" : request.getCurrency())
                .taxStatus(taxStatus.name())
                .igvRate(money2(igvRate))
                .igvIncluded(igvIncluded)
                .igvAmount(money2(igvAmount))

                .customerId(request.getCustomerId())
                .customerDocType(request.getCustomerDocType())
                .customerDocNumber(request.getCustomerDocNumber())
                .customerName(request.getCustomerName())
                .customerAddress(request.getCustomerAddress())
                .notes(request.getNotes())

                .subtotal(money2(subtotalBase))
                .discountTotal(money2(discountTotal))
                .total(money2(total))

                .status(ProformaStatus.PENDIENTE)
                .build();

        Proforma created = proformaRepository.create(proforma);

        for (ProformaItem it : items) {
            it.setProformaId(created.getId());
        }
        proformaItemRepository.batchCreate(items);

        return ProformaMapper.toResponse(created, items);
    }

    private void validateRequest(CreateProformaV2Request request) {
        if (request == null) throw new InvalidProformaV2Exception("Request requerido");
        if (request.getStationId() == null) throw new InvalidProformaV2Exception("stationId requerido");
        if (request.getCreatedBy() == null) throw new InvalidProformaV2Exception("createdBy requerido");
        if (request.getSeries() == null || request.getSeries().isBlank()) throw new InvalidProformaV2Exception("series requerido");
        if (request.getPriceList() == null) throw new InvalidProformaV2Exception("priceList requerido (A/B/C/D)");
        if (request.getItems() == null || request.getItems().isEmpty()) throw new InvalidProformaV2Exception("items requerido");

        if (request.getIgvRate() != null && request.getIgvRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidProformaV2Exception("igvRate inválido");
        }

        // Normalización fiscal
        TaxStatus taxStatus = request.getTaxStatus() != null ? request.getTaxStatus() : TaxStatus.NO_GRAVADA;
        if (taxStatus != TaxStatus.GRAVADA && Boolean.TRUE.equals(request.getIgvIncluded())) {
            // NO_GRAVADA no puede tener IGV incluido
            // Puedes normalizar o rechazar; aquí rechazo para consistencia.
            throw new InvalidProformaV2Exception("igvIncluded solo es válido cuando taxStatus=GRAVADA");
        }

        for (CreateProformaV2Request.Item it : request.getItems()) {
            if (it.getProductId() == null) throw new InvalidProformaV2Exception("productId requerido en items");
            if (it.getQuantity() == null || it.getQuantity().isBlank()) throw new InvalidProformaV2Exception("quantity requerido en items");
        }
    }

    private BigDecimal parseOrZero(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value);
    }

    private BigDecimal resolvePriceByList(ProductSnapshot p, Character priceList) {
        if (priceList == null) return null;
        return switch (priceList) {
            case 'A' -> p.getPriceA();
            case 'B' -> p.getPriceB();
            case 'C' -> p.getPriceC();
            case 'D' -> p.getPriceD();
            default -> null;
        };
    }

    /**
     * Dinero para persistencia/impresión: 2 decimales
     */
    private BigDecimal money2(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}

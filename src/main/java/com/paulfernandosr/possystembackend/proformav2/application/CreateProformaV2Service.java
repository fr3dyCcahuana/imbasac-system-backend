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

        LocalDate issueDate = request.getIssueDate() == null || request.getIssueDate().isBlank()
                ? LocalDate.now()
                : LocalDate.parse(request.getIssueDate());

        List<ProformaItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;

        int line = 1;
        for (CreateProformaV2Request.Item reqItem : request.getItems()) {
            ProductSnapshot p = productSnapshotRepository.getById(reqItem.getProductId());

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

            BigDecimal lineBase = unitPrice.multiply(qty);
            BigDecimal discountAmount = lineBase.multiply(discountPercent)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

            BigDecimal lineSubtotal = lineBase.subtract(discountAmount);

            ProformaItem item = ProformaItem.builder()
                    .proformaId(null) // set luego
                    .lineNumber(line)
                    .productId(p.getId())
                    .sku(p.getSku())
                    .description(p.getName())
                    .presentation(p.getPresentation())
                    .factor(p.getFactor())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .discountPercent(discountPercent)
                    .discountAmount(discountAmount)
                    .lineSubtotal(lineSubtotal)
                    .facturableSunat(p.getFacturableSunat())
                    .affectsStock(p.getAffectsStock())
                    .build();

            items.add(item);

            subtotal = subtotal.add(lineSubtotal);
            discountTotal = discountTotal.add(discountAmount);
            line++;
        }

        Proforma proforma = Proforma.builder()
                .stationId(request.getStationId())
                .createdBy(request.getCreatedBy())
                .series(request.getSeries())
                .number(number)
                .issueDate(issueDate)
                .priceList(request.getPriceList())
                .currency(request.getCurrency() == null || request.getCurrency().isBlank() ? "PEN" : request.getCurrency())
                .customerId(request.getCustomerId())
                .customerDocType(request.getCustomerDocType())
                .customerDocNumber(request.getCustomerDocNumber())
                .customerName(request.getCustomerName())
                .customerAddress(request.getCustomerAddress())
                .notes(request.getNotes())
                .subtotal(subtotal)
                .discountTotal(discountTotal)
                .total(subtotal)
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
}

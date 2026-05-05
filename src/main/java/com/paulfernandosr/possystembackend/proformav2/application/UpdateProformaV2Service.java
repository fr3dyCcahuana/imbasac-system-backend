package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.UpdateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProductSnapshotRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.UpdateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.ProductSnapshot;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
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
public class UpdateProformaV2Service implements UpdateProformaV2UseCase {

    private final ProformaRepository proformaRepository;
    private final ProformaItemRepository proformaItemRepository;
    private final ProductSnapshotRepository productSnapshotRepository;

    @Override
    @Transactional
    public ProformaV2Response update(Long proformaId, UpdateProformaV2Request request) {
        validateBasic(proformaId, request);

        Proforma locked = proformaRepository.lockById(proformaId)
                .orElseThrow(() -> new InvalidProformaV2Exception("Proforma no encontrada: " + proformaId));

        if (!ProformaStatus.PENDIENTE.equals(locked.getStatus())) {
            throw new InvalidProformaV2Exception(
                    "Solo se puede editar una proforma PENDIENTE. Estado actual: " + locked.getStatus()
            );
        }

        LocalDate issueDate = resolveIssueDate(request, locked);
        Character priceList = resolvePriceList(request, locked);
        String currency = resolveCurrency(request, locked);

        TaxStatus taxStatus = resolveTaxStatus(request, locked);
        BigDecimal igvRate = resolveIgvRate(request, locked);

        boolean igvIncluded = resolveIgvIncluded(request, locked, taxStatus);

        PaymentType paymentType = request.getPaymentType() != null
                ? request.getPaymentType()
                : (locked.getPaymentType() != null ? locked.getPaymentType() : PaymentType.CONTADO);

        Integer creditDays = paymentType == PaymentType.CREDITO
                ? (request.getCreditDays() != null ? request.getCreditDays() : locked.getCreditDays())
                : null;

        LocalDate dueDate = resolveDueDate(request, locked, paymentType);

        CalculatedItems calculated = buildItems(request.getItems(), priceList, taxStatus, igvRate, igvIncluded);

        Proforma toUpdate = Proforma.builder()
                .id(locked.getId())
                .stationId(locked.getStationId())
                .createdBy(locked.getCreatedBy())
                .series(locked.getSeries())
                .number(locked.getNumber())
                .issueDate(issueDate)
                .priceList(priceList)
                .currency(currency)

                .taxStatus(taxStatus.name())
                .igvRate(money2(igvRate))
                .igvIncluded(igvIncluded)
                .igvAmount(money2(calculated.igvAmount()))

                .customerId(request.getCustomerId() != null ? request.getCustomerId() : locked.getCustomerId())
                .customerDocType(request.getCustomerDocType() != null ? request.getCustomerDocType() : locked.getCustomerDocType())
                .customerDocNumber(request.getCustomerDocNumber() != null ? request.getCustomerDocNumber() : locked.getCustomerDocNumber())
                .customerName(request.getCustomerName() != null ? request.getCustomerName() : locked.getCustomerName())
                .customerAddress(request.getCustomerAddress() != null ? request.getCustomerAddress() : locked.getCustomerAddress())

                .paymentType(paymentType)
                .creditDays(creditDays)
                .dueDate(dueDate)
                .notes(request.getNotes() != null ? request.getNotes() : locked.getNotes())

                .subtotal(money2(calculated.subtotalBase()))
                .discountTotal(money2(calculated.discountTotal()))
                .total(money2(calculated.total()))
                .status(ProformaStatus.PENDIENTE)
                .build();

        proformaRepository.updateEditable(toUpdate);

        proformaItemRepository.deleteByProformaId(proformaId);
        for (ProformaItem item : calculated.items()) {
            item.setProformaId(proformaId);
        }
        proformaItemRepository.batchCreate(calculated.items());

        Proforma updated = proformaRepository.findById(proformaId)
                .orElseThrow(() -> new InvalidProformaV2Exception("Proforma no encontrada después de editar: " + proformaId));
        List<ProformaItem> updatedItems = proformaItemRepository.findByProformaId(proformaId);

        return ProformaMapper.toResponse(updated, updatedItems);
    }

    private void validateBasic(Long proformaId, UpdateProformaV2Request request) {
        if (proformaId == null) throw new InvalidProformaV2Exception("proformaId requerido");
        if (request == null) throw new InvalidProformaV2Exception("Request requerido");
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidProformaV2Exception("items requerido. Debe enviar la lista final de productos de la proforma");
        }

        if (request.getIgvRate() != null && request.getIgvRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidProformaV2Exception("igvRate inválido");
        }

        if (request.getCreditDays() != null && request.getCreditDays() < 0) {
            throw new InvalidProformaV2Exception("creditDays inválido");
        }

        if (request.getIssueDate() != null && !request.getIssueDate().isBlank()) {
            try {
                LocalDate.parse(request.getIssueDate());
            } catch (Exception ex) {
                throw new InvalidProformaV2Exception("issueDate inválido. Formato esperado: yyyy-MM-dd");
            }
        }

        if (request.getDueDate() != null && !request.getDueDate().isBlank()) {
            try {
                LocalDate.parse(request.getDueDate());
            } catch (Exception ex) {
                throw new InvalidProformaV2Exception("dueDate inválido. Formato esperado: yyyy-MM-dd");
            }
        }

        for (UpdateProformaV2Request.Item item : request.getItems()) {
            if (item.getProductId() == null) throw new InvalidProformaV2Exception("productId requerido en items");
            if (item.getQuantity() == null || item.getQuantity().isBlank()) throw new InvalidProformaV2Exception("quantity requerido en items");
        }
    }

    private LocalDate resolveIssueDate(UpdateProformaV2Request request, Proforma locked) {
        if (request.getIssueDate() != null && !request.getIssueDate().isBlank()) {
            return LocalDate.parse(request.getIssueDate());
        }
        return locked.getIssueDate() != null ? locked.getIssueDate() : LocalDate.now();
    }

    private Character resolvePriceList(UpdateProformaV2Request request, Proforma locked) {
        Character value = request.getPriceList() != null ? request.getPriceList() : locked.getPriceList();
        if (value == null) throw new InvalidProformaV2Exception("priceList requerido (A/B/C/D)");

        char normalized = Character.toUpperCase(value);
        if (normalized != 'A' && normalized != 'B' && normalized != 'C' && normalized != 'D') {
            throw new InvalidProformaV2Exception("priceList inválido. Valores permitidos: A/B/C/D");
        }
        return normalized;
    }

    private String resolveCurrency(UpdateProformaV2Request request, Proforma locked) {
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            return request.getCurrency();
        }
        if (locked.getCurrency() != null && !locked.getCurrency().isBlank()) {
            return locked.getCurrency();
        }
        return "PEN";
    }

    private TaxStatus resolveTaxStatus(UpdateProformaV2Request request, Proforma locked) {
        if (request.getTaxStatus() != null) {
            return request.getTaxStatus();
        }
        if (locked.getTaxStatus() != null && !locked.getTaxStatus().isBlank()) {
            return TaxStatus.valueOf(locked.getTaxStatus());
        }
        return TaxStatus.NO_GRAVADA;
    }

    private BigDecimal resolveIgvRate(UpdateProformaV2Request request, Proforma locked) {
        if (request.getIgvRate() != null) {
            return request.getIgvRate();
        }
        if (locked.getIgvRate() != null) {
            return locked.getIgvRate();
        }
        return new BigDecimal("18.00");
    }

    private boolean resolveIgvIncluded(UpdateProformaV2Request request, Proforma locked, TaxStatus taxStatus) {
        boolean requestedOrCurrent = request.getIgvIncluded() != null
                ? Boolean.TRUE.equals(request.getIgvIncluded())
                : Boolean.TRUE.equals(locked.getIgvIncluded());

        if (taxStatus != TaxStatus.GRAVADA) {
            if (requestedOrCurrent && request.getIgvIncluded() != null) {
                throw new InvalidProformaV2Exception("igvIncluded solo es válido cuando taxStatus=GRAVADA");
            }
            return false;
        }

        return requestedOrCurrent;
    }

    private LocalDate resolveDueDate(UpdateProformaV2Request request, Proforma locked, PaymentType paymentType) {
        if (paymentType != PaymentType.CREDITO) {
            return null;
        }
        if (request.getDueDate() != null && !request.getDueDate().isBlank()) {
            return LocalDate.parse(request.getDueDate());
        }
        return locked.getDueDate();
    }

    private CalculatedItems buildItems(
            List<UpdateProformaV2Request.Item> requestItems,
            Character priceList,
            TaxStatus taxStatus,
            BigDecimal igvRate,
            boolean igvIncluded
    ) {
        List<ProformaItem> items = new ArrayList<>();

        BigDecimal subtotalBase = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal igvAmount = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        int line = 1;
        for (UpdateProformaV2Request.Item reqItem : requestItems) {
            ProductSnapshot product = productSnapshotRepository.getById(reqItem.getProductId());
            if (product == null) {
                throw new InvalidProformaV2Exception("Producto no encontrado: " + reqItem.getProductId());
            }

            validateSerializableProduct(product);

            BigDecimal quantity = parsePositive(reqItem.getQuantity(), "Cantidad inválida en línea " + line);
            validateQuantityForSerializableProduct(product, quantity);

            BigDecimal unitPrice = resolveUnitPrice(
                    reqItem.getUnitPriceOverride(),
                    product,
                    priceList,
                    line
            );

            BigDecimal discountPercent = parseOrZero(reqItem.getDiscountPercent());
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidProformaV2Exception("Descuento inválido en línea " + line);
            }

            BigDecimal gross = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
            BigDecimal discountAmount = gross.multiply(discountPercent)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal grossAfterDiscount = gross.subtract(discountAmount).setScale(4, RoundingMode.HALF_UP);

            if (taxStatus == TaxStatus.GRAVADA && igvIncluded) {
                BigDecimal divisor = BigDecimal.ONE.add(
                        igvRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                );
                BigDecimal baseLine = grossAfterDiscount.divide(divisor, 4, RoundingMode.HALF_UP);
                BigDecimal igvLine = grossAfterDiscount.subtract(baseLine).setScale(4, RoundingMode.HALF_UP);

                subtotalBase = subtotalBase.add(baseLine);
                igvAmount = igvAmount.add(igvLine);
                total = total.add(grossAfterDiscount);
            } else {
                subtotalBase = subtotalBase.add(grossAfterDiscount);
            }

            discountTotal = discountTotal.add(discountAmount);

            ProformaItem item = ProformaItem.builder()
                    .proformaId(null)
                    .lineNumber(line)
                    .productId(product.getId())
                    .sku(product.getSku())
                    .description((reqItem.getDescription() != null && !reqItem.getDescription().isBlank())
                            ? reqItem.getDescription()
                            : product.getName())
                    .presentation((reqItem.getPresentation() != null && !reqItem.getPresentation().isBlank())
                            ? reqItem.getPresentation()
                            : product.getPresentation())
                    .factor((reqItem.getFactor() != null && !reqItem.getFactor().isBlank())
                            ? parsePositive(reqItem.getFactor(), "Factor inválido en línea " + line)
                            : product.getFactor())
                    .quantity(quantity)
                    .unitPrice(money2(unitPrice))
                    .discountPercent(discountPercent.setScale(4, RoundingMode.HALF_UP))
                    .discountAmount(money2(discountAmount))
                    .lineSubtotal(money2(grossAfterDiscount))
                    .facturableSunat(product.getFacturableSunat())
                    .affectsStock(product.getAffectsStock())
                    .build();

            items.add(item);
            line++;
        }

        subtotalBase = subtotalBase.setScale(4, RoundingMode.HALF_UP);
        discountTotal = discountTotal.setScale(4, RoundingMode.HALF_UP);
        igvAmount = igvAmount.setScale(4, RoundingMode.HALF_UP);
        total = total.setScale(4, RoundingMode.HALF_UP);

        if (taxStatus == TaxStatus.GRAVADA && !igvIncluded) {
            igvAmount = subtotalBase.multiply(igvRate)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            total = subtotalBase.add(igvAmount).setScale(4, RoundingMode.HALF_UP);
        }

        if (taxStatus != TaxStatus.GRAVADA) {
            igvAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            total = subtotalBase.setScale(4, RoundingMode.HALF_UP);
        }

        return new CalculatedItems(items, subtotalBase, discountTotal, igvAmount, total);
    }

    private void validateSerializableProduct(ProductSnapshot product) {
        if (!Boolean.TRUE.equals(product.getManageBySerial())) {
            return;
        }

        String category = product.getCategory();
        if (category == null || (!category.equalsIgnoreCase("MOTOR") && !category.equalsIgnoreCase("MOTOCICLETAS"))) {
            throw new InvalidProformaV2Exception(
                    "Producto inválido: manage_by_serial=true solo aplica a categoría MOTOR/MOTOCICLETAS. " +
                            "SKU=" + product.getSku() + ", category=" + category
            );
        }
    }

    private void validateQuantityForSerializableProduct(ProductSnapshot product, BigDecimal quantity) {
        if (!Boolean.TRUE.equals(product.getManageBySerial())) {
            return;
        }
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new InvalidProformaV2Exception(
                    "Cantidad debe ser entera para productos serializados (MOTOR/MOTOCICLETAS). SKU=" + product.getSku()
            );
        }
    }

    private BigDecimal resolveUnitPrice(
            BigDecimal unitPriceOverride,
            ProductSnapshot product,
            Character priceList,
            int line
    ) {
        BigDecimal unitPrice = unitPriceOverride != null
                ? unitPriceOverride
                : resolvePriceByList(product, priceList);

        if (unitPrice == null) {
            throw new InvalidProformaV2Exception(
                    "El producto " + product.getSku() + " no tiene precio para lista " + priceList
            );
        }

        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidProformaV2Exception("Precio unitario inválido en línea " + line);
        }

        return unitPrice;
    }

    private BigDecimal parsePositive(String value, String message) {
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (Exception ex) {
            throw new InvalidProformaV2Exception(message);
        }
        if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProformaV2Exception(message);
        }
        return parsed;
    }

    private BigDecimal parseOrZero(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (Exception ex) {
            throw new InvalidProformaV2Exception("Valor numérico inválido: " + value);
        }
    }

    private BigDecimal resolvePriceByList(ProductSnapshot product, Character priceList) {
        if (priceList == null) return null;
        return switch (Character.toUpperCase(priceList)) {
            case 'A' -> product.getPriceA();
            case 'B' -> product.getPriceB();
            case 'C' -> product.getPriceC();
            case 'D' -> product.getPriceD();
            default -> null;
        };
    }

    private BigDecimal money2(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record CalculatedItems(
            List<ProformaItem> items,
            BigDecimal subtotalBase,
            BigDecimal discountTotal,
            BigDecimal igvAmount,
            BigDecimal total
    ) {}
}

package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.ConvertProformaToSaleV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProductSnapshotRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.SaleReferenceRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.output.model.ProductSnapshot;
import com.paulfernandosr.possystembackend.salev2.application.CreateSaleV2Service;
import com.paulfernandosr.possystembackend.salev2.domain.model.*;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConvertProformaToSaleV2Service implements ConvertProformaToSaleV2UseCase {

    private final ProformaRepository proformaRepository;
    private final ProformaItemRepository proformaItemRepository;
    private final SaleReferenceRepository saleReferenceRepository;
    private final ProductSnapshotRepository productSnapshotRepository;
    private final CreateSaleV2Service createSaleV2Service;

    @Override
    @Transactional
    public ConvertProformaV2Response convert(Long proformaId, ConvertProformaV2Request request, String username) {

        if (request == null) throw new InvalidProformaV2Exception("Request requerido");
        if (request.getDocType() == null) throw new InvalidProformaV2Exception("docType requerido");
        if (request.getSeries() == null || request.getSeries().isBlank()) throw new InvalidProformaV2Exception("series requerido");
        if (request.getPaymentType() == null) throw new InvalidProformaV2Exception("paymentType requerido");

        Proforma p = proformaRepository.lockById(proformaId)
                .orElseThrow(() -> new InvalidProformaV2Exception("Proforma no encontrada: " + proformaId));

        if (p.getStatus() != ProformaStatus.PENDIENTE) {
            throw new InvalidProformaV2Exception("La proforma no está PENDIENTE. Estado actual: " + p.getStatus());
        }

        List<ProformaItem> items = proformaItemRepository.findByProformaId(proformaId);
        if (items.isEmpty()) {
            throw new InvalidProformaV2Exception("La proforma no tiene items");
        }

        // Heredar impuestos desde PROFORMA
        TaxStatus taxStatus = (p.getTaxStatus() == null || p.getTaxStatus().isBlank())
                ? TaxStatus.NO_GRAVADA
                : TaxStatus.valueOf(p.getTaxStatus());

        BigDecimal igvRate = (p.getIgvRate() == null) ? new BigDecimal("18.00") : p.getIgvRate();

        boolean igvIncluded = Boolean.TRUE.equals(p.getIgvIncluded()) && taxStatus == TaxStatus.GRAVADA;

        // Si el request manda algo distinto, lo rechazamos (coherencia)
        if (request.getTaxStatus() != null && request.getTaxStatus() != taxStatus) {
            throw new InvalidProformaV2Exception("La venta debe heredar taxStatus de la proforma. Proforma=" + taxStatus + ", request=" + request.getTaxStatus());
        }
        if (request.getIgvRate() != null && request.getIgvRate().compareTo(igvRate) != 0) {
            throw new InvalidProformaV2Exception("La venta debe heredar igvRate de la proforma. Proforma=" + igvRate + ", request=" + request.getIgvRate());
        }

        Map<Integer, List<Long>> serialsByLine = new HashMap<>();
        if (request.getSerials() != null) {
            for (ConvertProformaV2Request.LineSerials ls : request.getSerials()) {
                if (ls.getLineNumber() == null) continue;
                serialsByLine.put(ls.getLineNumber(), ls.getSerialUnitIds() == null ? List.of() : ls.getSerialUnitIds());
            }
        }

        // ✅ Validación de seriales: solo MOTOR/MOTOCICLETAS con manage_by_serial=true requieren IDs
        validateSerials(items, serialsByLine);

        SaleV2CreateRequest saleReq = SaleV2CreateRequest.builder()
                .stationId(p.getStationId())
                .docType(request.getDocType())
                .series(request.getSeries())
                .issueDate(LocalDate.now()) // emisión real de la venta
                .currency(p.getCurrency())
                .exchangeRate(null)
                .priceList(PriceList.valueOf(String.valueOf(p.getPriceList())))
                .customerId(p.getCustomerId())
                .customerDocType(p.getCustomerDocType())
                .customerDocNumber(p.getCustomerDocNumber())
                .customerName(p.getCustomerName())
                .customerAddress(p.getCustomerAddress())
                .taxStatus(taxStatus)
                .taxReason(null)
                .igvRate(igvRate)
                .igvIncluded(igvIncluded)
                .paymentType(request.getPaymentType())
                .creditDays(request.getCreditDays())
                .dueDate(request.getDueDate() != null && !request.getDueDate().isBlank() ? LocalDate.parse(request.getDueDate()) : null)
                .notes(p.getNotes())
                .items(mapItems(items, serialsByLine, request.getDocType()))
                .payment(null)
                .build();

        // Pago CONTADO: se requiere method
        if (request.getPaymentType() == PaymentType.CONTADO) {
            PaymentMethod method = request.getMethod() != null ? request.getMethod() : PaymentMethod.EFECTIVO;
            saleReq.setPayment(SaleV2CreateRequest.Payment.builder().method(method).build());
        }

        SaleV2DocumentResponse createdSale = createSaleV2Service.create(saleReq, username);

        saleReferenceRepository.create(createdSale.getSaleId(), p.getId());
        proformaRepository.updateStatus(p.getId(), ProformaStatus.CONVERTIDA.name());

        return ConvertProformaV2Response.builder()
                .proformaId(p.getId())
                .proformaStatus(ProformaStatus.CONVERTIDA.name())
                .saleId(createdSale.getSaleId())
                .saleDocType(createdSale.getDocType() != null ? createdSale.getDocType().name() : null)
                .saleSeries(createdSale.getSeries())
                .saleNumber(createdSale.getNumber())
                .build();
    }

    private List<SaleV2CreateRequest.Item> mapItems(
            List<ProformaItem> items,
            Map<Integer, List<Long>> serialsByLine,
            DocType targetDocType
    ) {
        List<SaleV2CreateRequest.Item> result = new ArrayList<>();

        boolean allowDiscount = (targetDocType == DocType.SIMPLE);

        for (ProformaItem it : items) {

            BigDecimal qty = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();

            BigDecimal unitPriceOverride;
            BigDecimal discountPercent;

            if (allowDiscount) {
                // SIMPLE: conserva descuento y precio snapshot
                unitPriceOverride = it.getUnitPrice();
                discountPercent = it.getDiscountPercent();
            } else {
                // BOLETA/FACTURA: descuento debe ser 0.
                discountPercent = BigDecimal.ZERO;

                // Convertir descuento a precio ajustado para mantener coherencia:
                // lineSubtotal es "grossAfterDiscount" en la semántica del unitPrice de la proforma.
                if (qty.compareTo(BigDecimal.ZERO) > 0 && it.getLineSubtotal() != null) {
                    unitPriceOverride = it.getLineSubtotal().divide(qty, 4, RoundingMode.HALF_UP);
                } else {
                    unitPriceOverride = it.getUnitPrice();
                }
            }

            result.add(SaleV2CreateRequest.Item.builder()
                    .productId(it.getProductId())
                    .quantity(it.getQuantity())
                    .discountPercent(discountPercent)
                    .unitPriceOverride(unitPriceOverride)
                    .lineKind(LineKind.VENDIDO)
                    .giftReason(null)
                    .serialUnitIds(serialsByLine.getOrDefault(it.getLineNumber(), null))
                    .build());
        }
        return result;
    }

    private void validateSerials(List<ProformaItem> items, Map<Integer, List<Long>> serialsByLine) {
        for (ProformaItem it : items) {

            ProductSnapshot p = productSnapshotRepository.getById(it.getProductId());
            if (p == null) {
                throw new InvalidProformaV2Exception("Producto no encontrado: " + it.getProductId());
            }
            boolean serializable = Boolean.TRUE.equals(p.getManageBySerial());

            List<Long> serialIds = serialsByLine.get(it.getLineNumber());

            // Si NO es serializable, no debe venir serialUnitIds
            if (!serializable) {
                if (serialIds != null && !serialIds.isEmpty()) {
                    throw new InvalidProformaV2Exception(
                            "No se pueden asignar seriales a un producto no serializado. " +
                                    "SKU=" + p.getSku() + ", line=" + it.getLineNumber()
                    );
                }
                continue;
            }

            // Guardrail: solo MOTOR/MOTOCICLETAS pueden ser serializados
            String cat = p.getCategory();
            if (cat == null || (!cat.equalsIgnoreCase("MOTOR") && !cat.equalsIgnoreCase("MOTOCICLETAS"))) {
                throw new InvalidProformaV2Exception(
                        "Producto inválido: manage_by_serial=true solo aplica a categoría MOTOR/MOTOCICLETAS. " +
                                "SKU=" + p.getSku() + ", category=" + cat
                );
            }

            // Cantidad debe ser entera y debe coincidir con la cantidad de seriales
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidProformaV2Exception("Cantidad inválida en línea " + it.getLineNumber());
            }
            if (it.getQuantity().stripTrailingZeros().scale() > 0) {
                throw new InvalidProformaV2Exception(
                        "Cantidad debe ser entera para productos serializados. SKU=" + p.getSku() + ", line=" + it.getLineNumber()
                );
            }

            int expected;
            try {
                expected = it.getQuantity().intValueExact();
            } catch (ArithmeticException ex) {
                throw new InvalidProformaV2Exception(
                        "Cantidad inválida para producto serializado. SKU=" + p.getSku() + ", line=" + it.getLineNumber()
                );
            }

            if (serialIds == null || serialIds.isEmpty()) {
                throw new InvalidProformaV2Exception(
                        "Debe enviar serialUnitIds para producto serializado. " +
                                "SKU=" + p.getSku() + ", line=" + it.getLineNumber()
                );
            }

            // Duplicados
            Set<Long> uniq = new HashSet<>(serialIds);
            if (uniq.size() != serialIds.size()) {
                throw new InvalidProformaV2Exception(
                        "serialUnitIds contiene duplicados. SKU=" + p.getSku() + ", line=" + it.getLineNumber()
                );
            }

            if (serialIds.size() != expected) {
                throw new InvalidProformaV2Exception(
                        "La cantidad de serialUnitIds no coincide con quantity. " +
                                "SKU=" + p.getSku() + ", line=" + it.getLineNumber() + ", quantity=" + expected + ", serials=" + serialIds.size()
                );
            }
        }
    }
}

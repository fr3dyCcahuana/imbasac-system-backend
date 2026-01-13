package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.ConvertProformaToSaleV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaItemRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.ProformaRepository;
import com.paulfernandosr.possystembackend.proformav2.domain.port.output.SaleReferenceRepository;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Response;
import com.paulfernandosr.possystembackend.salev2.application.CreateSaleV2Service;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.domain.model.LineKind;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConvertProformaToSaleV2Service implements ConvertProformaToSaleV2UseCase {

    private final ProformaRepository proformaRepository;
    private final ProformaItemRepository proformaItemRepository;
    private final SaleReferenceRepository saleReferenceRepository;

    // Reutilizamos el flujo completo de venta v2 (stock, kardex, seriales, CxC, validaciones, etc.)
    private final CreateSaleV2Service createSaleV2Service;

    @Override
    @Transactional
    public ConvertProformaV2Response convert(Long proformaId, ConvertProformaV2Request request, String username) {
        if (request == null) throw new InvalidProformaV2Exception("Request requerido");
        if (request.getDocType() == null) throw new InvalidProformaV2Exception("docType requerido");
        if (request.getSeries() == null || request.getSeries().isBlank()) throw new InvalidProformaV2Exception("series requerido");
        if (request.getTaxStatus() == null) throw new InvalidProformaV2Exception("taxStatus requerido");
        if (request.getIgvRate() == null) request.setIgvRate(new BigDecimal("18.00"));
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

        Map<Integer, List<Long>> serialsByLine = new HashMap<>();
        if (request.getSerials() != null) {
            for (ConvertProformaV2Request.LineSerials ls : request.getSerials()) {
                if (ls.getLineNumber() == null) continue;
                serialsByLine.put(ls.getLineNumber(), ls.getSerialUnitIds() == null ? List.of() : ls.getSerialUnitIds());
            }
        }

        SaleV2CreateRequest saleReq = SaleV2CreateRequest.builder()
                .stationId(p.getStationId())
                .docType(request.getDocType())
                .series(request.getSeries())
                .issueDate(LocalDate.now()) // emisión real de la venta
                .currency(p.getCurrency())
                .exchangeRate(null)
                .priceList(com.paulfernandosr.possystembackend.salev2.domain.model.PriceList.valueOf(String.valueOf(p.getPriceList())))
                .customerId(p.getCustomerId())
                .customerDocType(p.getCustomerDocType())
                .customerDocNumber(p.getCustomerDocNumber())
                .customerName(p.getCustomerName())
                .customerAddress(p.getCustomerAddress())
                .taxStatus(request.getTaxStatus())
                .taxReason(null)
                .igvRate(request.getIgvRate())
                .paymentType(request.getPaymentType())
                .creditDays(request.getCreditDays())
                .dueDate(request.getDueDate() != null && !request.getDueDate().isBlank() ? LocalDate.parse(request.getDueDate()) : null)
                .notes(p.getNotes())
                .items(mapItems(items, serialsByLine))
                .payment(null) // CONTADO/CREDITO se maneja dentro de SaleV2; si CONTADO, controller ya manda payment.method
                .build();

        // Si es CONTADO, el frontend debe enviar payment.method; pero para convertir, asumimos EFECTIVO si no viene
        if (request.getPaymentType() == com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType.CONTADO) {
            saleReq.setPayment(SaleV2CreateRequest.Payment.builder()
                    .method(com.paulfernandosr.possystembackend.salev2.domain.model.PaymentMethod.EFECTIVO)
                    .build());
        }

        SaleV2DocumentResponse createdSale = createSaleV2Service.create(saleReq, username);

        // Guardar referencia
        saleReferenceRepository.create(createdSale.getSaleId(), p.getId());

        // Marcar proforma convertida
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

    private List<SaleV2CreateRequest.Item> mapItems(List<ProformaItem> items, Map<Integer, List<Long>> serialsByLine) {
        List<SaleV2CreateRequest.Item> result = new ArrayList<>();
        for (ProformaItem it : items) {
            result.add(SaleV2CreateRequest.Item.builder()
                    .productId(it.getProductId())
                    .quantity(it.getQuantity())
                    .discountPercent(it.getDiscountPercent())
                    .lineKind(LineKind.VENDIDO)
                    .giftReason(null)
                    .serialUnitIds(serialsByLine.getOrDefault(it.getLineNumber(), null))
                    .build());
        }
        return result;
    }
}

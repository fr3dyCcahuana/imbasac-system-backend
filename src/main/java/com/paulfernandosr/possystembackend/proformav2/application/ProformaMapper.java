package com.paulfernandosr.possystembackend.proformav2.application;

import com.paulfernandosr.possystembackend.proformav2.domain.Proforma;
import com.paulfernandosr.possystembackend.proformav2.domain.ProformaItem;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;

import java.util.List;
import java.util.stream.Collectors;

public class ProformaMapper {

    private ProformaMapper() {}

    public static ProformaV2Response toResponse(Proforma p, List<ProformaItem> items) {
        return ProformaV2Response.builder()
                .id(p.getId())
                .stationId(p.getStationId())
                .createdBy(p.getCreatedBy())
                .series(p.getSeries())
                .number(p.getNumber())
                .issueDate(p.getIssueDate() != null ? p.getIssueDate().toString() : null)
                .priceList(p.getPriceList())
                .currency(p.getCurrency())
                .customerId(p.getCustomerId())
                .customerDocType(p.getCustomerDocType())
                .customerDocNumber(p.getCustomerDocNumber())
                .customerName(p.getCustomerName())
                .customerAddress(p.getCustomerAddress())
                .notes(p.getNotes())
                .subtotal(p.getSubtotal())
                .discountTotal(p.getDiscountTotal())
                .total(p.getTotal())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .items(items == null ? null : items.stream().map(ProformaMapper::toItem).collect(Collectors.toList()))
                .build();
    }

    private static ProformaV2Response.Item toItem(ProformaItem it) {
        return ProformaV2Response.Item.builder()
                .id(it.getId())
                .lineNumber(it.getLineNumber())
                .productId(it.getProductId())
                .sku(it.getSku())
                .description(it.getDescription())
                .presentation(it.getPresentation())
                .factor(it.getFactor())
                .quantity(it.getQuantity())
                .unitPrice(it.getUnitPrice())
                .discountPercent(it.getDiscountPercent())
                .discountAmount(it.getDiscountAmount())
                .lineSubtotal(it.getLineSubtotal())
                .facturableSunat(it.getFacturableSunat())
                .affectsStock(it.getAffectsStock())
                .build();
    }
}

package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper;

import com.paulfernandosr.possystembackend.sale.domain.SaleDocument;
import com.paulfernandosr.possystembackend.sale.domain.SaleItem;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.UnitOfMeasureType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

public class DocumentIssuerMapper {
    public static final BigDecimal IGV_FACTOR = new BigDecimal("1.18");
    public static final BigDecimal IGV_RATE = new BigDecimal("0.18");

    public static BigDecimal calculateTotalBasePrice(Collection<SaleItem> saleItems) {
        return saleItems.stream()
                .map(saleItem -> calculateBasePrice(saleItem.getPrice())
                        .multiply(new BigDecimal(saleItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateBasePrice(BigDecimal price) {
        return price.divide(IGV_FACTOR, 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateTotalIgv(BigDecimal totalBasePrice) {
        return totalBasePrice
                .multiply(IGV_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateGlobalDiscount(BigDecimal totalDiscount) {
        return calculateBasePrice(totalDiscount)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static Collection<SaleDocument.Item> mapItems(Collection<SaleItem> saleItems) {
        return saleItems
                .stream()
                .map(DocumentIssuerMapper::mapItem)
                .toList();
    }

    public static SaleDocument.Item mapItem(SaleItem saleItem) {
        return SaleDocument.Item.builder()
                .productName(saleItem.getProduct().getName())
                .unitOfMeasure(UnitOfMeasureType.PRODUCT_UNIT.getCode())
                .quantity(saleItem.getQuantity())
                .unitPrice(saleItem.getPrice())
                .build();
    }
}

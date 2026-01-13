package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SaleV2Repository {
    Long insertSale(Long stationId,
                    Long saleSessionId,
                    Long createdBy,
                    String docType,
                    String series,
                    Long number,
                    LocalDate issueDate,
                    String currency,
                    BigDecimal exchangeRate,
                    String priceList,
                    Long customerId,
                    String customerDocType,
                    String customerDocNumber,
                    String customerName,
                    String customerAddress,
                    String taxStatus,
                    String taxReason,
                    BigDecimal igvRate,
                    String paymentType,
                    Integer creditDays,
                    LocalDate dueDate,
                    String notes);

    Long insertSaleItem(Long saleId,
                       Integer lineNumber,
                       Long productId,
                       String sku,
                       String description,
                       String presentation,
                       BigDecimal factor,
                       BigDecimal quantity,
                       BigDecimal unitPrice,
                       BigDecimal discountPercent,
                       BigDecimal discountAmount,
                       String lineKind,
                       String giftReason,
                       Boolean facturableSunat,
                       Boolean affectsStock,
                       Boolean visibleInDocument,
                       BigDecimal unitCostSnapshot,
                       BigDecimal totalCostSnapshot,
                       BigDecimal revenueTotal);

    void updateTotals(Long saleId,
                      BigDecimal subtotal,
                      BigDecimal discountTotal,
                      BigDecimal igvAmount,
                      BigDecimal total,
                      BigDecimal giftCostTotal);
}

package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PriceList;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SaleV2Repository {

    Long insertSale(
            Long stationId,
            Long saleSessionId,
            Long createdBy,
            DocType docType,
            String series,
            Long number,
            LocalDate issueDate,
            String currency,
            BigDecimal exchangeRate,
            PriceList priceList,
            Long customerId,
            String customerDocType,
            String customerDocNumber,
            String customerName,
            String customerAddress,
            TaxStatus taxStatus,
            String taxReason,
            BigDecimal igvRate,
            PaymentType paymentType,
            Integer creditDays,
            LocalDate dueDate,
            String notes
    );

    Long insertSaleItem(
            Long saleId,
            int lineNumber,
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
            boolean facturableSunat,
            boolean affectsStock,
            boolean visibleInDocument,
            BigDecimal unitCostSnapshot,
            BigDecimal totalCostSnapshot,
            BigDecimal revenueTotal
    );

    void updateTotals(Long saleId,
                      BigDecimal subtotal,
                      BigDecimal discountTotal,
                      BigDecimal igvAmount,
                      BigDecimal total,
                      BigDecimal giftCostTotal);

    void insertPayment(Long saleId, String method, BigDecimal amount);

    void addIncomeToOpenSession(Long userId, BigDecimal salesIncome, BigDecimal totalDiscount);
}

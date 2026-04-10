package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleV2CounterSaleCompositionRepository {

    List<LockedCounterSaleForComposition> lockCounterSales(List<Long> counterSaleIds);

    void reserveCounterSale(Long saleId,
                            Long counterSaleId,
                            BigDecimal counterSaleTotal,
                            BigDecimal counterSaleDiscountTotal,
                            Long reservedBy,
                            String reservedByUsername,
                            String editReason);

    void releaseCounterSales(Long saleId,
                             List<Long> counterSaleIds,
                             String releaseReason);

    void finalizeAcceptedCounterSale(Long saleId,
                                     Long counterSaleId,
                                     String emittedDocType,
                                     String emittedSeries,
                                     Long emittedNumber);

    void insertAcceptedCounterSaleItem(Long saleId,
                                       Long counterSaleId,
                                       Long counterSaleItemId,
                                       Integer sourceLineNumber,
                                       Long productId,
                                       String sku,
                                       String description,
                                       BigDecimal quantity,
                                       BigDecimal originalUnitPrice,
                                       BigDecimal emittedUnitPrice,
                                       BigDecimal originalRevenueTotal,
                                       BigDecimal emittedRevenueTotal);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class LockedCounterSaleForComposition {
        private Long id;
        private String series;
        private Long number;
        private String status;
        private Boolean associatedToSunat;
        private Long associatedSaleId;
        private String associatedDocType;
        private String associatedSeries;
        private Long associatedNumber;
        private BigDecimal total;
        private BigDecimal discountTotal;
        private LocalDateTime associatedAt;
    }
}

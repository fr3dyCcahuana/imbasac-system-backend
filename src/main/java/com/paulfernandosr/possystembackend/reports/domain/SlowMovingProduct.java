package com.paulfernandosr.possystembackend.reports.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SlowMovingProduct(long productId, String sku, String name, String model,
                                BigDecimal stock, long countSales, BigDecimal soldQty,
                                BigDecimal returnedQty, BigDecimal netQty, long historicalSales,
                                LocalDate lastSaleDate, Integer daysWithoutSale,
                                LocalDate firstStockDate, Integer daysSinceFirstStock,
                                boolean newInPeriod, BigDecimal priceA, BigDecimal priceB,
                                BigDecimal priceC, BigDecimal priceD, BigDecimal averageSalePrice) {
}

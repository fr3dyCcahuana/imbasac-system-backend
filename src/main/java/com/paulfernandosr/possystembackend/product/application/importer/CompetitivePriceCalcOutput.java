package com.paulfernandosr.possystembackend.product.application.importer;

import java.math.BigDecimal;

public record CompetitivePriceCalcOutput(
        boolean ok,
        BigDecimal priceA,
        BigDecimal priceB,
        BigDecimal priceC,
        BigDecimal priceD
) {}

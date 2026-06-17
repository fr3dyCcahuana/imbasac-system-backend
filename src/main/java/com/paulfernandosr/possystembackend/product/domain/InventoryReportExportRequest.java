package com.paulfernandosr.possystembackend.product.domain;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record InventoryReportExportRequest(
        @NotNull InventoryReportFormat format,
        @NotNull LocalDate dateFrom,
        @NotNull LocalDate dateTo,
        @NotEmpty List<Long> productIds
) {
}

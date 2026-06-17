package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.InventoryReportExportRequest;

public interface ExportInventoryReportUseCase {
    byte[] export(InventoryReportExportRequest request);
}

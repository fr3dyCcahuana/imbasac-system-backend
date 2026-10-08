package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductCatalogExcelExportRequest;

public interface ExportProductCatalogExcelUseCase {
    byte[] export(ProductCatalogExcelExportRequest request);
}

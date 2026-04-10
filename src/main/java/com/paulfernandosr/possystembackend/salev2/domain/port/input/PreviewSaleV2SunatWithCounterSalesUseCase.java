package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ComposeSunatPreviewResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2ComposeSunatRequest;

public interface PreviewSaleV2SunatWithCounterSalesUseCase {
    SaleV2ComposeSunatPreviewResponse preview(Long saleId, SaleV2ComposeSunatRequest request);
}

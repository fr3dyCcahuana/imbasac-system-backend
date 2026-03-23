package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2AdminEditRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;

public interface AdminEditSaleV2BeforeSunatUseCase {
    SaleV2DocumentResponse edit(Long saleId, SaleV2AdminEditRequest request, String username);
}

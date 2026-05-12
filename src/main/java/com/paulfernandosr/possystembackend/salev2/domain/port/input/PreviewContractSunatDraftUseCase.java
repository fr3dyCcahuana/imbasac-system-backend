package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftPreviewResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftSaveRequest;

public interface PreviewContractSunatDraftUseCase {
    ContractSunatDraftPreviewResponse preview(Long saleId, ContractSunatDraftSaveRequest request);
}

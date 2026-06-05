package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteCreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteResponse;

public interface CreateCreditNoteUseCase {
    CreditNoteResponse create(Long saleId, CreditNoteCreateRequest request, String username);
}

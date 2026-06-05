package com.paulfernandosr.possystembackend.salev2.domain.port.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteResponse;

import java.util.List;

public interface GetSaleCreditNotesUseCase {
    List<CreditNoteResponse> getBySaleId(Long saleId);
}

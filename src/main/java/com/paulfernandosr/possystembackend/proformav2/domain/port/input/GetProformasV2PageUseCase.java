package com.paulfernandosr.possystembackend.proformav2.domain.port.input;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;

public interface GetProformasV2PageUseCase {
    PageResponse<ProformaV2SummaryResponse> findPage(String status, String query, int page, int size);
}

package com.paulfernandosr.possystembackend.proformav2.domain.port.input;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaCreatorResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;

import java.time.LocalDate;
import java.util.List;

public interface GetProformasV2PageUseCase {
    PageResponse<ProformaV2SummaryResponse> findPage(
            String status,
            String query,
            Long createdBy,
            Boolean edited,
            String paymentType,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size
    );

    List<ProformaCreatorResponse> findCreators();
}

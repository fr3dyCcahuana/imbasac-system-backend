package com.paulfernandosr.possystembackend.proformav2.domain.port.output;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;

import java.util.List;

public interface ProformaV2QueryRepository {
    long countPage(String status, String likeParam);
    List<ProformaV2SummaryResponse> findPage(String status, String likeParam, int limit, int offset);
}

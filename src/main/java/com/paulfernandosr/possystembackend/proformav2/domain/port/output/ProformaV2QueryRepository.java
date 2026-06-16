package com.paulfernandosr.possystembackend.proformav2.domain.port.output;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaCreatorResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaQueryFilters;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;

import java.util.List;

public interface ProformaV2QueryRepository {
    long countPage(ProformaQueryFilters filters);
    List<ProformaV2SummaryResponse> findPage(ProformaQueryFilters filters, int limit, int offset);
    List<ProformaCreatorResponse> findCreators();
}

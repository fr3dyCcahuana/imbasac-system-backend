package com.paulfernandosr.possystembackend.proformav2.domain.port.input;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.UpdateProformaV2Request;

public interface UpdateProformaV2UseCase {
    ProformaV2Response update(Long proformaId, UpdateProformaV2Request request);
}

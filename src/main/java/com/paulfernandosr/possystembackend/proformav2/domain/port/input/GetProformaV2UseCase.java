package com.paulfernandosr.possystembackend.proformav2.domain.port.input;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;

public interface GetProformaV2UseCase {
    ProformaV2Response getById(Long proformaId);
}

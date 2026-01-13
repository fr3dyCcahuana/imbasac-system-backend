package com.paulfernandosr.possystembackend.proformav2.domain.port.input;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.CreateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;

public interface CreateProformaV2UseCase {
    ProformaV2Response create(CreateProformaV2Request request);
}

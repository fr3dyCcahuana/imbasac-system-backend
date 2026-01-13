package com.paulfernandosr.possystembackend.proformav2.domain.port.input;

import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Response;

public interface ConvertProformaToSaleV2UseCase {
    ConvertProformaV2Response convert(Long proformaId, ConvertProformaV2Request request, String username);
}

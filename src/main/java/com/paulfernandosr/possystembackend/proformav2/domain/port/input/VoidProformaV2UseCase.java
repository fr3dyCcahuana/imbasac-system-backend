package com.paulfernandosr.possystembackend.proformav2.domain.port.input;

import com.paulfernandosr.possystembackend.proformav2.domain.model.VoidProformaV2Response;

public interface VoidProformaV2UseCase {
    VoidProformaV2Response voidProforma(Long proformaId, String reason);
}

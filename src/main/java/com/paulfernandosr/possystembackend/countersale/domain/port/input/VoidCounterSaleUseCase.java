package com.paulfernandosr.possystembackend.countersale.domain.port.input;

import com.paulfernandosr.possystembackend.countersale.domain.model.VoidCounterSaleResponse;

public interface VoidCounterSaleUseCase {
    VoidCounterSaleResponse voidSale(Long counterSaleId, String reason, String username);
}

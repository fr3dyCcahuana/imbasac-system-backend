package com.paulfernandosr.possystembackend.countersale.domain.port.input;

import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleSunatCombinationEmitResponse;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleSunatCombinationRequest;

public interface EmitCounterSaleSunatCombinationUseCase {
    CounterSaleSunatCombinationEmitResponse emit(Long anchorCounterSaleId,
                                                 CounterSaleSunatCombinationRequest request,
                                                 String username);
}

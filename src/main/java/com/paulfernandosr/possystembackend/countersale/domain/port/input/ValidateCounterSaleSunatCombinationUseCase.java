package com.paulfernandosr.possystembackend.countersale.domain.port.input;

import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleSunatCombinationRequest;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleSunatCombinationValidationResponse;

public interface ValidateCounterSaleSunatCombinationUseCase {
    CounterSaleSunatCombinationValidationResponse validate(Long anchorCounterSaleId,
                                                           CounterSaleSunatCombinationRequest request);
}

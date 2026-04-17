package com.paulfernandosr.possystembackend.countersale.application;

import com.paulfernandosr.possystembackend.countersale.domain.port.input.ValidateCounterSaleSunatCombinationUseCase;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleSunatCombinationRequest;
import com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto.CounterSaleSunatCombinationValidationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ValidateCounterSaleSunatCombinationService implements ValidateCounterSaleSunatCombinationUseCase {

    private final CounterSaleSunatCombinationComposer composer;

    @Override
    public CounterSaleSunatCombinationValidationResponse validate(Long anchorCounterSaleId,
                                                                  CounterSaleSunatCombinationRequest request) {
        return composer.toValidationResponse(composer.compose(anchorCounterSaleId, request));
    }
}

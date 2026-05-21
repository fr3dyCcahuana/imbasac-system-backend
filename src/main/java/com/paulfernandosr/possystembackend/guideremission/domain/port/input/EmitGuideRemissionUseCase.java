package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionEmissionResponse;

public interface EmitGuideRemissionUseCase {
    GuideRemissionEmissionResponse emit(String serie, String numero);
}

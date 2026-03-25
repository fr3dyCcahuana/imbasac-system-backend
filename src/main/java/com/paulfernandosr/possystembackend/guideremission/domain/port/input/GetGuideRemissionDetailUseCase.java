package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDetailResponse;

public interface GetGuideRemissionDetailUseCase {
    GuideRemissionDetailResponse getBySerieAndNumero(String serie, String numero);
}

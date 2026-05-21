package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDetailResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionFullFlowRequest;

public interface UpdateGuideRemissionUseCase {
    GuideRemissionDetailResponse update(String serie, String numero, GuideRemissionFullFlowRequest request);
}

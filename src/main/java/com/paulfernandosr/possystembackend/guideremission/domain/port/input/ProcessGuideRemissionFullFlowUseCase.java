package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionFullFlowRequest;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionFullFlowResponse;

public interface ProcessGuideRemissionFullFlowUseCase {
    GuideRemissionFullFlowResponse process(GuideRemissionFullFlowRequest request);
}

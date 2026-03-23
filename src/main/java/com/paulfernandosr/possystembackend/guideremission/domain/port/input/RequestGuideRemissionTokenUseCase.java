package com.paulfernandosr.possystembackend.guideremission.domain.port.input;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenRequest;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;

public interface RequestGuideRemissionTokenUseCase {
    GuideRemissionTokenResponse requestToken(GuideRemissionTokenRequest request);
}

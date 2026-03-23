package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenRequest;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.RequestGuideRemissionTokenUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequestGuideRemissionTokenService implements RequestGuideRemissionTokenUseCase {
    private final GuideRemissionProvider guideRemissionProvider;

    @Override
    public GuideRemissionTokenResponse requestToken(GuideRemissionTokenRequest request) {
        return guideRemissionProvider.requestToken(request);
    }
}

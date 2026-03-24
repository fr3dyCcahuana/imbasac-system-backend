package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.RequestGuideRemissionTokenUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequestGuideRemissionTokenService implements RequestGuideRemissionTokenUseCase {
    private final GuideRemissionTokenManager guideRemissionTokenManager;

    @Override
    public GuideRemissionTokenResponse requestToken() {
        return guideRemissionTokenManager.getOrCreateToken().getTokenResponse();
    }
}

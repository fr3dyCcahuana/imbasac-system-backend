package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GuideRemissionTokenResolution {
    private GuideRemissionTokenResponse tokenResponse;
    private String tokenSource;

    public String accessToken() {
        return tokenResponse.getAccessToken();
    }
}

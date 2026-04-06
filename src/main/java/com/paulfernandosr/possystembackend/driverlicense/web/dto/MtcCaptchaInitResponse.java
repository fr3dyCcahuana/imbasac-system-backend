package com.paulfernandosr.possystembackend.driverlicense.web.dto;

public record MtcCaptchaInitResponse(
        String sessionId,
        String captchaImage
) {
}
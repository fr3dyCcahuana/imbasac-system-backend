package com.paulfernandosr.possystembackend.driverlicense.domain;

public record DriverLicenseQuery(
        String sessionId,
        String documentType,
        String documentNumber,
        String captchaText
) {
}
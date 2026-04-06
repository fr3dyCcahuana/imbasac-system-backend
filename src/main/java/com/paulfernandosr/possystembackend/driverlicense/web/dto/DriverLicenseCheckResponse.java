package com.paulfernandosr.possystembackend.driverlicense.web.dto;

import java.util.List;

public record DriverLicenseCheckResponse(
        boolean success,
        String status,
        String message,
        String fullName,
        String documentType,
        String documentNumber,
        String licenseNumber,
        String licenseClassCategory,
        String validUntil,
        String verySeriousFaults,
        String seriousFaults,
        String accumulatedPoints,
        String remainingPointsToMax,
        String accumulatedInfractions,
        List<String> infractions,
        List<String> procedures,
        String source,
        boolean captchaRequired
) {
}
package com.paulfernandosr.possystembackend.driverlicense.domain;

import java.util.List;

public record DriverLicenseResult(
        boolean success,
        DriverLicenseStatus status,
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
    public static DriverLicenseResult error(String message) {
        return new DriverLicenseResult(
                false,
                DriverLicenseStatus.ERROR,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "MTC_WEB",
                false
        );
    }

    public static DriverLicenseResult notFound(String message, String documentType, String documentNumber) {
        return new DriverLicenseResult(
                true,
                DriverLicenseStatus.NO_ENCONTRADA,
                message,
                null,
                documentType,
                documentNumber,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "MTC_WEB",
                false
        );
    }
}
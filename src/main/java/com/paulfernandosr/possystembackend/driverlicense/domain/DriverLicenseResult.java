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
        boolean captchaRequired,
        String errorCode,
        boolean controlledError
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
                false,
                null,
                false
        );
    }

    public static DriverLicenseResult captchaInvalid(String message, String documentType, String documentNumber) {
        return new DriverLicenseResult(
                false,
                DriverLicenseStatus.CAPTCHA_INVALIDO,
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
                true,
                "MTC_CAPTCHA_INVALIDO",
                true
        );
    }

    public static DriverLicenseResult controlledError(String message,
                                                      String documentType,
                                                      String documentNumber,
                                                      String errorCode,
                                                      boolean captchaRequired) {
        return new DriverLicenseResult(
                false,
                DriverLicenseStatus.ERROR_CONTROLADO,
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
                captchaRequired,
                errorCode,
                true
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
                false,
                null,
                false
        );
    }
}

package com.paulfernandosr.possystembackend.driverlicense.domain;

public enum DriverLicenseStatus {
    VIGENTE,
    VENCIDA,
    SUSPENDIDA,
    CANCELADA,
    NO_ENCONTRADA,
    SIN_LICENCIA,
    CAPTCHA_INVALIDO,
    ERROR_CONTROLADO,
    ERROR
}

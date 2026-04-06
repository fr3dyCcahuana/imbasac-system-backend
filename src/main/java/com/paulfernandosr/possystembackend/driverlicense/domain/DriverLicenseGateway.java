package com.paulfernandosr.possystembackend.driverlicense.domain;

public interface DriverLicenseGateway {
    DriverLicenseResult check(DriverLicenseQuery query);
}
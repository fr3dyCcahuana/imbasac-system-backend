package com.paulfernandosr.possystembackend.driverlicense.application;

import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseQuery;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseResult;

public interface CheckDriverLicenseUseCase {
    DriverLicenseResult check(DriverLicenseQuery query);
}
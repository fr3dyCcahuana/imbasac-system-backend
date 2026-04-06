package com.paulfernandosr.possystembackend.driverlicense.web.mapper;

import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseQuery;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseResult;
import com.paulfernandosr.possystembackend.driverlicense.web.dto.DriverLicenseCheckRequest;
import com.paulfernandosr.possystembackend.driverlicense.web.dto.DriverLicenseCheckResponse;
import org.springframework.stereotype.Component;

@Component
public class DriverLicenseWebMapper {

    public DriverLicenseQuery toQuery(DriverLicenseCheckRequest request) {
        return new DriverLicenseQuery(
                request.getSessionId(),
                request.getDocumentType(),
                request.getDocumentNumber(),
                request.getCaptchaText()
        );
    }

    public DriverLicenseCheckResponse toResponse(DriverLicenseResult result) {
        return new DriverLicenseCheckResponse(
                result.success(),
                result.status().name(),
                result.message(),
                result.fullName(),
                result.documentType(),
                result.documentNumber(),
                result.licenseNumber(),
                result.licenseClassCategory(),
                result.validUntil(),
                result.verySeriousFaults(),
                result.seriousFaults(),
                result.accumulatedPoints(),
                result.remainingPointsToMax(),
                result.accumulatedInfractions(),
                result.infractions(),
                result.procedures(),
                result.source(),
                result.captchaRequired()
        );
    }
}
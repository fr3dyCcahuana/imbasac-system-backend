package com.paulfernandosr.possystembackend.driverlicense.application;

import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseGateway;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseQuery;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CheckDriverLicenseService implements CheckDriverLicenseUseCase {

    private final DriverLicenseGateway gateway;

    public CheckDriverLicenseService(DriverLicenseGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public DriverLicenseResult check(DriverLicenseQuery query) {
        if (query == null) {
            return DriverLicenseResult.error("La consulta no puede ser nula.");
        }
        if (!StringUtils.hasText(query.sessionId())) {
            return DriverLicenseResult.error("El sessionId es obligatorio.");
        }
        if (!StringUtils.hasText(query.documentType())) {
            return DriverLicenseResult.error("El tipo de documento es obligatorio.");
        }
        if (!StringUtils.hasText(query.documentNumber())) {
            return DriverLicenseResult.error("El número de documento es obligatorio.");
        }
        if (!StringUtils.hasText(query.captchaText())) {
            return DriverLicenseResult.error("El captcha es obligatorio.");
        }
        return gateway.check(query);
    }
}
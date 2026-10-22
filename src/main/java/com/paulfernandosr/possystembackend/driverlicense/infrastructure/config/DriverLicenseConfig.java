package com.paulfernandosr.possystembackend.driverlicense.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MtcLicenseProperties.class,
        com.paulfernandosr.possystembackend.driverlicense.assisted.MtcAssistedProperties.class})
public class DriverLicenseConfig {
}

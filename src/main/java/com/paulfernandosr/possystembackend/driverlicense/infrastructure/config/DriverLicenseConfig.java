package com.paulfernandosr.possystembackend.driverlicense.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MtcLicenseProperties.class)
public class DriverLicenseConfig {
}
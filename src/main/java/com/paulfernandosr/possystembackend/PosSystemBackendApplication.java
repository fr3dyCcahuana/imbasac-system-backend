package com.paulfernandosr.possystembackend;

import com.paulfernandosr.possystembackend.driverlicense.infrastructure.config.MtcLicenseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MtcLicenseProperties.class)
public class PosSystemBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PosSystemBackendApplication.class, args);
	}

}

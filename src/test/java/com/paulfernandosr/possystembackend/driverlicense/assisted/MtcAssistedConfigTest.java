package com.paulfernandosr.possystembackend.driverlicense.assisted;

import com.paulfernandosr.possystembackend.driverlicense.infrastructure.config.DriverLicenseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MtcAssistedConfigTest {
    @Test void wiresTheActualConsultationWithoutDatabaseOrEvidenceStorage() {
        new ApplicationContextRunner()
                .withUserConfiguration(DriverLicenseConfig.class, MtcAssistedController.class,
                        MtcAssistedService.class, MtcAssistedBrowserFactory.class, MtcAssistedParser.class)
                .withPropertyValues("integration.mtc-license.assisted.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MtcAssistedController.class);
                    assertThat(context.getBean(MtcAssistedService.class).status().enabled()).isTrue();
                });
    }
}

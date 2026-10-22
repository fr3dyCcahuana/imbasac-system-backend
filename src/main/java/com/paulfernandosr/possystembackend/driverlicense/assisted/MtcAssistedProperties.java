package com.paulfernandosr.possystembackend.driverlicense.assisted;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "integration.mtc-license.assisted")
public class MtcAssistedProperties {
    private boolean enabled = false;
    private String searchUrl = "https://licencias.mtc.gob.pe/#/index";
    private long sessionTtlSeconds = 180;
    private int maxSessions = 3;
}

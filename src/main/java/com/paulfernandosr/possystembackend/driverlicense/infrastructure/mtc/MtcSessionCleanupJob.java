package com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc;

import com.paulfernandosr.possystembackend.driverlicense.infrastructure.config.MtcLicenseProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MtcSessionCleanupJob {

    private final MtcSessionManager sessionManager;
    private final MtcCaptchaService captchaService;
    private final MtcLicenseProperties properties;

    public MtcSessionCleanupJob(MtcSessionManager sessionManager,
                                MtcCaptchaService captchaService,
                                MtcLicenseProperties properties) {
        this.sessionManager = sessionManager;
        this.captchaService = captchaService;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanup() {
        // Placeholder: Redis TTL es la fuente de verdad lógica.
        // Si quieres limpieza agresiva de memoria, aquí puedes iterar sesiones y
        // cerrar las que superen ttl + gracia. Se deja simple para evitar cerrar
        // una sesión recién creada antes de guardar TTL.
    }
}
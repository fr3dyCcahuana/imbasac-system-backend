package com.paulfernandosr.possystembackend.accessaudit.infrastructure;

import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
public class AccessAuditWebConfig {
    @Bean AccessAuditCaptureFilter accessAuditCaptureFilter(AccessAuditService audit,AuditRequestMetadata metadata) {
        return new AccessAuditCaptureFilter(audit,metadata);
    }
    @Bean FilterRegistrationBean<AccessAuditCaptureFilter> auditServletRegistration(AccessAuditCaptureFilter filter) {
        var registration=new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
}

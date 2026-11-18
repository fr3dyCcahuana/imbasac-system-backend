package com.paulfernandosr.possystembackend.deviceaccess.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class DevicePolicyWebConfig {
    @Bean DevicePolicyFilter devicePolicyFilter(DeviceAccessPolicyService policy, ObjectMapper mapper,org.springframework.beans.factory.ObjectProvider<com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard> guards) {
        return new DevicePolicyFilter(policy,mapper,guards.getIfAvailable());
    }
    @Bean FilterRegistrationBean<DevicePolicyFilter> devicePolicyServletRegistration(DevicePolicyFilter filter) {
        var registration=new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Run exactly once, inside Spring Security after JWT authentication.
        return registration;
    }
}

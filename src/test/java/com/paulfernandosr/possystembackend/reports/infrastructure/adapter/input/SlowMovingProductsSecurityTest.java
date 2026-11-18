package com.paulfernandosr.possystembackend.reports.infrastructure.adapter.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import com.paulfernandosr.possystembackend.accessaudit.infrastructure.AccessAuditCaptureFilter;
import com.paulfernandosr.possystembackend.accessaudit.infrastructure.AuditRequestMetadata;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException;
import com.paulfernandosr.possystembackend.deviceaccess.infrastructure.DevicePolicyFilter;
import com.paulfernandosr.possystembackend.reports.domain.port.input.GetSlowMovingProductsUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.security.infrastructure.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SlowMovingProductsSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private GetSlowMovingProductsUseCase reports;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "security.token.expiration=300", "security.token.secret=test-only-placeholder");
        context.register(SecurityConfig.class, Beans.class);
        context.refresh();
        reports = context.getBean(GetSlowMovingProductsUseCase.class);
        when(reports.models()).thenReturn(List.of("PULSAR"));
        when(reports.preview(any(), anyInt())).thenReturn(new GetSlowMovingProductsUseCase.Preview(List.of(), false, OffsetDateTime.now()));
        when(reports.excel(any())).thenReturn(new byte[]{1, 2, 3});
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void asynchronousCompletionRetainsTheInitialRequestAuthorization() throws Exception {
        var initial = mvc.perform(get("/async-security-probe")
                        .header("Authorization", "Bearer dashboard"))
                .andExpect(request().asyncStarted()).andReturn();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk()).andExpect(content().string("completed"));
    }

    @Test
    void asynchronousEndpointStillRequiresAuthenticationOnTheInitialRequest() throws Exception {
        mvc.perform(get("/async-security-probe")).andExpect(status().isUnauthorized());
    }

    @org.springframework.web.bind.annotation.RestController
    static class AsyncProbe {
        @org.springframework.web.bind.annotation.GetMapping("/async-security-probe")
        java.util.concurrent.Callable<String> completeLater() { return () -> "completed"; }
    }

    @Test
    void anonymousAndUnprivilegedUsersCannotReadModelsPreviewOrExcel() throws Exception {
        for (String suffix : new String[]{"", "/models", "/excel"}) {
            mvc.perform(get("/reports/products/slow-moving" + suffix).param("from", "2025-01-01").param("to", "2025-03-31"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/reports/products/slow-moving" + suffix).param("from", "2025-01-01").param("to", "2025-03-31")
                            .header("Authorization", "Bearer unrelated"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(reports);
    }

    @Test
    void dashboardReaderCanReadAndExportWhileExemptFromDevicePolicy() throws Exception {
        mvc.perform(get("/reports/products/slow-moving/models").header("Authorization", "Bearer dashboard"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("PULSAR"));
        mvc.perform(get("/reports/products/slow-moving").param("from", "2025-01-01").param("to", "2025-03-31")
                        .param("status", "NEVER_SOLD").header("Authorization", "Bearer dashboard"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/reports/products/slow-moving/excel").param("from", "2025-01-01").param("to", "2025-03-31")
                        .header("Authorization", "Bearer dashboard"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("productos_poca_rotacion_")));
    }

    @Test
    void expiredSessionAndRequiredDevicePolicyCannotBeBypassedWithDashboardPrivilege() throws Exception {
        when(context.getBean(SessionRepository.class).isActive(anyString(), anyString())).thenReturn(false);
        mvc.perform(get("/reports/products/slow-moving/models").header("Authorization", "Bearer dashboard"))
                .andExpect(status().isUnauthorized());
        when(context.getBean(SessionRepository.class).isActive(anyString(), anyString())).thenReturn(true);
        var policy = context.getBean(DeviceAccessPolicyService.class);
        when(policy.isEnabled()).thenReturn(true);
        doThrow(new DevicePolicyException(DevicePolicyException.Code.DEVICE_CERTIFICATE_CHANNEL_NOT_READY))
                .when(policy).checkUsername("dashboard");
        mvc.perform(get("/reports/products/slow-moving/models").header("Authorization", "Bearer dashboard"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    @Test
    void rejectsInvalidOrMissingFiltersBeforeRunningTheReport() throws Exception {
        mvc.perform(get("/reports/products/slow-moving").param("from", "2025-01-01").param("to", "2025-03-31")
                        .param("sort", "UNKNOWN").header("Authorization", "Bearer dashboard"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/reports/products/slow-moving/excel").header("Authorization", "Bearer dashboard"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Configuration
    @EnableWebMvc
    static class Beans {
        @Bean AsyncProbe asyncProbe() { return new AsyncProbe(); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean GetSlowMovingProductsUseCase reports() { return mock(GetSlowMovingProductsUseCase.class); }
        @Bean SlowMovingProductsRestController controller(GetSlowMovingProductsUseCase reports) { return new SlowMovingProductsRestController(reports); }
        @Bean DeviceAccessPolicyService policy() { return mock(DeviceAccessPolicyService.class); }
        @Bean DevicePolicyFilter device(DeviceAccessPolicyService policy, ObjectMapper mapper) { return new DevicePolicyFilter(policy, mapper); }
        @Bean AccessAuditCaptureFilter audit() { return new AccessAuditCaptureFilter(mock(AccessAuditService.class), new AuditRequestMetadata()); }
        @Bean UserDetailsService users() {
            return name -> User.withUsername(name).password("unused")
                    .authorities(name.equals("dashboard") ? "MANAGE_DASHBOARD" : "MANAGE_PRODUCTS").build();
        }
        @Bean JsonWebTokenUtils tokens() {
            var tokens = mock(JsonWebTokenUtils.class);
            when(tokens.parseAccessToken("dashboard")).thenReturn(new JsonWebTokenUtils.AccessTokenIdentity("dashboard", "a".repeat(64)));
            when(tokens.parseAccessToken("unrelated")).thenReturn(new JsonWebTokenUtils.AccessTokenIdentity("unrelated", "b".repeat(64)));
            return tokens;
        }
        @Bean SessionRepository sessions() {
            var sessions = mock(SessionRepository.class);
            when(sessions.isActive(anyString(), anyString())).thenReturn(true);
            return sessions;
        }
        @Bean SecurityAuthFilter jwt(JsonWebTokenUtils tokens, UserDetailsService users, SessionRepository sessions) {
            return new SecurityAuthFilter(tokens, users, sessions);
        }
        @Bean SecurityAuthEntryPoint entry(ObjectMapper mapper) { return new SecurityAuthEntryPoint(mapper); }
        @Bean SecurityAccessDeniedHandler denied(ObjectMapper mapper) { return new SecurityAccessDeniedHandler(mapper); }
    }
}

package com.paulfernandosr.possystembackend.deviceaccess.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException;
import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.security.infrastructure.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.bind.annotation.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DevicePolicySecurityChainTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private DeviceAccessPolicyService policy;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "security.token.expiration=300", "security.token.secret=test-only-placeholder");
        context.register(SecurityConfig.class, DevicePolicyWebConfig.class, TestBeans.class,
            com.paulfernandosr.possystembackend.accessaudit.infrastructure.AccessAuditQueryExceptionHandler.class,
            com.paulfernandosr.possystembackend.accessaudit.infrastructure.AccessAuditExceptionHandler.class);context.refresh();
        policy=context.getBean(DeviceAccessPolicyService.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { if(context!=null) context.close(); }
    @Test void activePolicyRunsAfterJwtAndBeforeTheBusinessController() throws Exception {
        when(policy.isEnabled()).thenReturn(true);
        doThrow(new DevicePolicyException(DevicePolicyException.Code.DEVICE_CERTIFICATE_CHANNEL_NOT_READY)).when(policy).checkUsername("user");
        mvc.perform(get("/device-test").header("Authorization","Bearer test"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_CERTIFICATE_CHANNEL_NOT_READY"));
        verify(policy).checkUsername("user");
    }
    @Test void disabledFeatureLeavesTheExistingAuthenticatedFlowWorking() throws Exception {
        mvc.perform(get("/device-test").header("Authorization","Bearer test"))
            .andExpect(status().isOk()).andExpect(content().string("business"));
        verify(policy,never()).checkUsername(anyString());
    }
    @Test void missingAuthenticationStillUsesTheExisting401Boundary() throws Exception {
        when(policy.isEnabled()).thenReturn(true);
        mvc.perform(get("/device-test")).andExpect(status().isUnauthorized());
        verify(policy,never()).checkUsername(anyString());
    }
    @Test void logoutRemainsAvailableEvenIfTheCurrentPolicyWouldDenyAccess() throws Exception {
        when(policy.isEnabled()).thenReturn(true);
        doThrow(new DevicePolicyException(DevicePolicyException.Code.DEVICE_CERTIFICATE_CHANNEL_NOT_READY)).when(policy).checkUsername(anyString());
        mvc.perform(post("/auth/logout").servletPath("/auth/logout").header("Authorization","Bearer test"))
            .andExpect(status().isOk()).andExpect(content().string("logout"));
        verify(policy,never()).checkUsername(anyString());
    }
    @Test void anonymousAuditAndBrowserReportRequireAuthentication() throws Exception {
        mvc.perform(get("/security/access-audit/status")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/client-validation").contentType("application/json").content("{\"completed\":true}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void authenticatedCommercialPermissionDoesNotAllowAuditAccess() throws Exception {
        mvc.perform(get("/security/access-audit/status").header("Authorization","Bearer test")).andExpect(status().isForbidden());
    }
    @Test void auditReaderGetsPrivateStatusButCannotExport() throws Exception {
        when(context.getBean(UserDetailsService.class).loadUserByUsername("user"))
            .thenReturn(User.withUsername("user").password("unused").authorities("VIEW_SECURITY_AUDIT").build());
        when(context.getBean(com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService.class).status())
            .thenReturn(new com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.Status(false,"America/Lima",null,31,5000,false,"BACKEND_CONNECTION",5));
        mvc.perform(get("/security/access-audit/status").header("Authorization","Bearer test"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/security/access-audit/events/export").param("from","2026-10-29").param("to","2026-10-29").header("Authorization","Bearer test"))
            .andExpect(status().isForbidden());
    }
    @Test void exportPermissionAloneDoesNotBypassTheReadBoundary() throws Exception {
        when(context.getBean(UserDetailsService.class).loadUserByUsername("user"))
            .thenReturn(User.withUsername("user").password("unused").authorities("EXPORT_SECURITY_AUDIT").build());
        mvc.perform(get("/security/access-audit/events/export").param("from","2026-10-29").param("to","2026-10-29").header("Authorization","Bearer test"))
            .andExpect(status().isForbidden());
    }
    @Configuration @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class TestBeans {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean DeviceAccessPolicyService policy() { return mock(DeviceAccessPolicyService.class); }
        @Bean com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService auditService() {
            return mock(com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService.class);
        }
        @Bean com.paulfernandosr.possystembackend.accessaudit.infrastructure.AccessAuditRestController auditController(com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService service) {
            return new com.paulfernandosr.possystembackend.accessaudit.infrastructure.AccessAuditRestController(service);
        }
        @Bean com.paulfernandosr.possystembackend.accessaudit.infrastructure.AccessAuditCaptureFilter auditFilter(com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService service) {
            return new com.paulfernandosr.possystembackend.accessaudit.infrastructure.AccessAuditCaptureFilter(
                service,
                new com.paulfernandosr.possystembackend.accessaudit.infrastructure.AuditRequestMetadata());
        }
        @Bean UserDetailsService users() {
            UserDetailsService users=mock(UserDetailsService.class);
            when(users.loadUserByUsername("user")).thenReturn(User.withUsername("user").password("unused").authorities("READ").build());
            return users;
        }
        @Bean JsonWebTokenUtils tokens() {
            JsonWebTokenUtils tokens=mock(JsonWebTokenUtils.class);
            when(tokens.parseAccessToken("test")).thenReturn(new JsonWebTokenUtils.AccessTokenIdentity("user","a".repeat(64)));
            return tokens;
        }
        @Bean SessionRepository sessions() {
            SessionRepository sessions=mock(SessionRepository.class);
            when(sessions.isActive("a".repeat(64),"user")).thenReturn(true);return sessions;
        }
        @Bean SecurityAuthFilter jwt(JsonWebTokenUtils tokens,UserDetailsService users,SessionRepository sessions) {
            return new SecurityAuthFilter(tokens,users,sessions);
        }
        @Bean SecurityAuthEntryPoint entry(ObjectMapper mapper) { return new SecurityAuthEntryPoint(mapper); }
        @Bean SecurityAccessDeniedHandler denied(ObjectMapper mapper) { return new SecurityAccessDeniedHandler(mapper); }
        @Bean ProbeController probe() { return new ProbeController(); }
    }
    @RestController static class ProbeController {
        @GetMapping("/device-test") String business() { return "business"; }
        @PostMapping("/auth/logout") String logout() { return "logout"; }
    }
}

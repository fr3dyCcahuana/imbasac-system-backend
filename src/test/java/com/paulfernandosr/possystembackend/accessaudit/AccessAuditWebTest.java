package com.paulfernandosr.possystembackend.accessaudit;

import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.*;
import com.paulfernandosr.possystembackend.accessaudit.infrastructure.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccessAuditWebTest {
    private final AccessAuditService audit=mock(AccessAuditService.class);
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new AccessAuditRestController(audit))
            .setControllerAdvice(new AccessAuditExceptionHandler(),new AccessAuditQueryExceptionHandler()).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void permissions(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin",null,
            Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
    @Test void viewPermissionIsRequiredEvenForAnAdministratorOfOtherModules() throws Exception {
        permissions("MANAGE_DASHBOARD","MANAGE_USERS");
        mvc.perform(get("/security/access-audit/status")).andExpect(status().isForbidden()); verifyNoInteractions(audit);
    }
    @Test void exporterWithoutReadCannotReadTheHistory() throws Exception {
        permissions("EXPORT_SECURITY_AUDIT");
        mvc.perform(get("/security/access-audit/events/export").param("from","2026-10-29").param("to","2026-10-29"))
            .andExpect(status().isForbidden()); verifyNoInteractions(audit);
    }
    @Test void readerCannotExportOrRevoke() throws Exception {
        permissions("VIEW_SECURITY_AUDIT");
        mvc.perform(get("/security/access-audit/events/export").param("from","2026-10-29").param("to","2026-10-29"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/security/access-audit/sessions/"+UUID.randomUUID()+"/revoke").contentType("application/json").content("{\"reason\":\"Administrative review\"}"))
            .andExpect(status().isForbidden()); verifyNoInteractions(audit);
    }
    @Test void privateStatusCannotBeCached() throws Exception {
        permissions("VIEW_SECURITY_AUDIT");
        when(audit.status()).thenReturn(new Status(false,"America/Lima",null,31,5000,false,"BACKEND_CONNECTION",5));
        mvc.perform(get("/security/access-audit/status")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void invalidDatesDoNotBecomeServerErrorsOrQueries() throws Exception {
        permissions("VIEW_SECURITY_AUDIT");
        mvc.perform(get("/security/access-audit/events").param("from","not-a-day").param("to","2026-10-29"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(audit);
    }
    @Test void sqlFailureIsSanitizedAndNotReturnedAsAnEmptyHistory() throws Exception {
        permissions("VIEW_SECURITY_AUDIT"); when(audit.status()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SECRET_DATABASE_CONNECTION"));
        mvc.perform(get("/security/access-audit/status")).andExpect(status().isServiceUnavailable())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET_DATABASE_CONNECTION"))));
    }
    @Test void forwardedHeadersCannotInventTheClientAddress() {
        var request=new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1"); request.addHeader("X-Forwarded-For","1.2.3.4");
        request.addHeader("User-Agent","Browser\r\n"+"x".repeat(500));
        var provider=new AuditRequestMetadata(); var meta=provider.from(request);
        assertThat(meta.peerAddress()).isEqualTo("127.0.0.1"); assertThat(meta.userAgent()).hasSize(300).doesNotContain("\r","\n");
        assertThat(provider.from(request).correlationId()).isEqualTo(meta.correlationId());
    }
    @Test void frameworkForwardedWrappersCannotOverwriteTheTransportPeer() {
        var original=new MockHttpServletRequest();original.setRemoteAddr("127.0.0.1");
        var forwarded=new jakarta.servlet.http.HttpServletRequestWrapper(original) {
            @Override public String getRemoteAddr() { return "1.2.3.4"; }
        };
        assertThat(new AuditRequestMetadata().from(forwarded).peerAddress()).isEqualTo("127.0.0.1");
    }
    @Test void successfulPrivateReadsAreAuditedWithTheVerifiedIdentityAndPeer() throws Exception {
        when(audit.isEnabled()).thenReturn(true);
        var request=new MockHttpServletRequest("GET","/security/access-audit/events");request.setServletPath("/security/access-audit/events");
        request.setAttribute("imbasac.authenticatedUsername","admin");
        new AccessAuditCaptureFilter(audit,new AuditRequestMetadata()).doFilter(request,new org.springframework.mock.web.MockHttpServletResponse(),(req,res)->{});
        verify(audit).viewed(eq("admin"),any());
    }
    @Test void malformedLoginIsRecordedWithoutReadingItsBodyOrInventingAnIdentity() throws Exception {
        when(audit.isEnabled()).thenReturn(true);
        var request=new MockHttpServletRequest("POST","/auth/login");request.setServletPath("/auth/login");
        new AccessAuditCaptureFilter(audit,new AuditRequestMetadata()).doFilter(request,new org.springframework.mock.web.MockHttpServletResponse(),
            (req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(400));
        verify(audit).denied(eq("LOGIN_REJECTED"),isNull(),any());
    }
    @Test void handledLoginFailureIsNotCountedTwiceByTheFilter() throws Exception {
        when(audit.isEnabled()).thenReturn(true);
        var request=new MockHttpServletRequest("POST","/auth/login");request.setServletPath("/auth/login");request.setAttribute("imbasac.loginHandled",true);
        new AccessAuditCaptureFilter(audit,new AuditRequestMetadata()).doFilter(request,new org.springframework.mock.web.MockHttpServletResponse(),
            (req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(403));
        verify(audit,never()).denied(anyString(),any(),any());
    }
    @Test void rejectedSessionKeepsOnlyIdentityFromAVerifiedTokenAndDoesNotInventLoginCredentials() throws Exception {
        when(audit.isEnabled()).thenReturn(true);
        var request=new MockHttpServletRequest("GET","/users/me"); request.setServletPath("/users/me");
        request.addHeader("Authorization","Bearer not-logged");
        new AccessAuditCaptureFilter(audit,new AuditRequestMetadata()).doFilter(request,new org.springframework.mock.web.MockHttpServletResponse(),(req,res)->{
            req.setAttribute("imbasac.verifiedTokenUsername","LENIN");
            req.setAttribute("imbasac.verifiedTokenSessionKey","a".repeat(64));
            req.setAttribute(com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.ATTRIBUTE,"SESSION_NOT_ACTIVE");
            ((jakarta.servlet.http.HttpServletResponse)res).setStatus(401);
        });
        verify(audit).denied(eq("SESSION_REJECTED"),eq("LENIN"),eq("a".repeat(64)),eq("SESSION_NOT_ACTIVE"),any());
    }
    @Test void aRejectedLoginPreservesTheTypedNonexistentUserButNeverCopiesThePassword() {
        var login=mock(com.paulfernandosr.possystembackend.security.domain.port.input.LoginUseCase.class);
        var repository=mock(com.paulfernandosr.possystembackend.accessaudit.domain.port.AccessAuditRepository.class);
        var meta=new com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.Metadata("127.0.0.1","Browser",UUID.randomUUID());
        var actualAudit=new AccessAuditService(repository,()->meta,true,(key,user)->{});
        var controller=new com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.AuthRestController(login,null,null,actualAudit);
        String secret="DO_NOT_RETAIN_THIS_INPUT";
        when(login.login("nonexistent-user",secret)).thenThrow(new com.paulfernandosr.possystembackend.security.domain.exception.InvalidCredentialsException("DO_NOT_RETAIN_THIS_INPUT"));
        var request=new com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.request.LoginRequest("nonexistent-user",secret);
        assertThatThrownBy(()->controller.login(request,new MockHttpServletRequest())).isInstanceOf(RuntimeException.class);
        verify(repository).record("LOGIN_REJECTED","REJECTED","BACKEND",null,"nonexistent-user",null,null,"CREDENTIALS_OR_ACCOUNT_REJECTED",meta,true);
    }
    @Test void metadataReadsLateFailureReasonWithoutChangingItsCorrelationOrCopyingCredentialHeaders() {
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer DO_NOT_COPY");
        var provider=new AuditRequestMetadata(); var original=provider.from(request);
        request.setAttribute(com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.ATTRIBUTE,"CERTIFICATE_MISSING");
        var after=provider.from(request);
        assertThat(after.correlationId()).isEqualTo(original.correlationId());
        assertThat(after.authentication().failureReason()).isEqualTo("CERTIFICATE_MISSING");
        assertThat(after.toString()).doesNotContain("DO_NOT_COPY");
    }
    @Test void certificateFailureOnANormalRequestIsNotMislabelledAsMissingModulePermissions() throws Exception {
        when(audit.isEnabled()).thenReturn(true);
        var request=new MockHttpServletRequest("GET","/users/me"); request.setServletPath("/users/me");
        request.setAttribute("imbasac.authenticatedUsername","LENIN");
        new AccessAuditCaptureFilter(audit,new AuditRequestMetadata()).doFilter(request,new org.springframework.mock.web.MockHttpServletResponse(),(req,res)->{
            req.setAttribute(com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.ATTRIBUTE,"CERTIFICATE_MISSING");
            ((jakarta.servlet.http.HttpServletResponse)res).setStatus(403);
        });
        verify(audit).denied(eq("ACCESS_DENIED"),eq("LENIN"),isNull(),eq("CERTIFICATE_MISSING"),any());
    }
}

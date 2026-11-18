package com.paulfernandosr.possystembackend.deviceaccess.application;

import com.paulfernandosr.possystembackend.accesscontrol.application.AccessControlService;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException;
import com.paulfernandosr.possystembackend.deviceaccess.domain.*;
import com.paulfernandosr.possystembackend.deviceaccess.domain.port.output.DevicePolicyRepository;
import com.paulfernandosr.possystembackend.devicecertificate.*;
import com.paulfernandosr.possystembackend.role.domain.*;
import com.paulfernandosr.possystembackend.user.domain.*;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.paulfernandosr.possystembackend.trustedclient.*;
import com.paulfernandosr.possystembackend.security.application.LoginService;
import com.paulfernandosr.possystembackend.security.domain.port.output.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificatePolicyIntegrationTest {
    private final DevicePolicyRepository policies=mock(DevicePolicyRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final DeviceCertificateService certificates=mock(DeviceCertificateService.class);
    private final AccessControlService control=mock(AccessControlService.class);
    private final TrustedClientResolver resolver=mock(TrustedClientResolver.class);
    private final User user=User.builder().id(7L).username("client").enabled(true).role(new Role()).build();
    private final UUID device=UUID.randomUUID();
    private DeviceAccessPolicyService policy;
    @BeforeEach void setup() {
        user.getRole().setId(5L);user.getRole().setName(RoleName.CLIENTE);
        when(policies.findByRole(5L,RoleName.CLIENTE)).thenReturn(Optional.of(DeviceAccessMode.CERTIFICATE_REQUIRED));
        when(users.findByUsername("client")).thenReturn(Optional.of(user));when(control.isEnabled()).thenReturn(true);
        when(certificates.isReady()).thenReturn(true);when(certificates.presentedDevice()).thenReturn(device);
        policy=new DeviceAccessPolicyService(policies,users,true,certificates,control,resolver);
    }
    @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
    @Test void requiredAuthorizedClientUsesVerifiedPcAndNeverBrowserHash() {
        when(control.isDeviceAuthorized(user,device)).thenReturn(true);
        var request=new MockHttpServletRequest();RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        policy.check(user);assertThat(request.getAttribute("imbasac.authorizedDeviceId")).isEqualTo(device);
    }
    @Test void validCertificateDoesNotAuthorizeDifferentClientOfSameSharedPc() {
        assertThatThrownBy(()->policy.check(user)).isInstanceOfSatisfying(DevicePolicyException.class,e->assertThat(e.getCode()).isEqualTo(DevicePolicyException.Code.DEVICE_NOT_AUTHORIZED));
    }
    @Test void exemptRoleDoesNotRequireCertificateButStillChecksAccountBlock() {
        when(policies.findByRole(5L,RoleName.CLIENTE)).thenReturn(Optional.of(DeviceAccessMode.PASSWORD_ONLY));policy.check(user);
        verify(control).checkAccount(user);verify(certificates,never()).presentedDevice();
    }
    @Test void accountRestrictionIsEnforcedEvenWhileDevicePolicyRemainsOff() {
        var policyOff=new DeviceAccessPolicyService(policies,users,false,certificates,control,resolver);
        doThrow(new AccessControlException(AccessControlException.Code.ACCESS_ACCOUNT_BLOCKED)).when(control).checkAccount(user);
        assertThat(policyOff.isEnabled()).isTrue();assertThatThrownBy(()->policyOff.check(user)).isInstanceOf(AccessControlException.class);
    }
    @Test void regularAuthenticatedRequestAndRenewalDenialDoNotCreateStrikes() {
        assertThatThrownBy(()->policy.check(user)).isInstanceOf(DevicePolicyException.class);
        assertThatThrownBy(()->policy.checkUsername("client")).isInstanceOf(DevicePolicyException.class);
        verify(control,never()).rejectUnauthorizedDevice(any(),any(),any(),any());
    }
    @Test void incorrectPasswordCannotAssignStrikeToNamedVictim() {
        var credentials=mock(AuthenticationPort.class);var tokens=mock(TokenGenerationPort.class);var sessions=mock(SessionRepository.class);
        var login=new LoginService(credentials,tokens,sessions,users,policy);
        assertThatThrownBy(()->login.login("client","incorrect")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(control,certificates,resolver,sessions,tokens);
    }
    @Test void onlyCredentialVerifiedLoginWithTrustedProxyCanRecordStrike() {
        UUID attempt=UUID.randomUUID();when(resolver.current()).thenReturn(new TrustedClientContext(true,"203.0.113.7",attempt,"NONE",null));
        var credentials=mock(AuthenticationPort.class);when(credentials.authenticate("client","correct")).thenReturn(true);
        var sessions=mock(SessionRepository.class);var tokens=mock(TokenGenerationPort.class);
        assertThatThrownBy(()->new LoginService(credentials,tokens,sessions,users,policy).login("client","correct")).isInstanceOf(DevicePolicyException.class);
        verify(control).rejectUnauthorizedDevice(eq(user),isNull(),eq(attempt),matches("[a-f0-9]{64}"));verifyNoInteractions(sessions,tokens);
    }
    @Test void untrustedHeaderAndTechnicalCertificateErrorsNeverBecomeStrikes() {
        when(resolver.current()).thenReturn(new TrustedClientContext(false,"127.0.0.1",UUID.randomUUID(),"UNTRUSTED",null));
        policy.recordUnauthorizedLogin(user,new DevicePolicyException(DevicePolicyException.Code.DEVICE_NOT_AUTHORIZED));
        policy.recordUnauthorizedLogin(user,new DevicePolicyException(DevicePolicyException.Code.DEVICE_CERTIFICATE_INVALID));
        verify(control,never()).rejectUnauthorizedDevice(any(),any(),any(),any());
    }
}

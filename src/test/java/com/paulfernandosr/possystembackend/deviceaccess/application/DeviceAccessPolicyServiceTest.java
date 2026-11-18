package com.paulfernandosr.possystembackend.deviceaccess.application;
import com.paulfernandosr.possystembackend.deviceaccess.domain.*;
import com.paulfernandosr.possystembackend.deviceaccess.domain.port.output.DevicePolicyRepository;
import com.paulfernandosr.possystembackend.role.domain.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeviceAccessPolicyServiceTest {
    private final DevicePolicyRepository policies=mock(DevicePolicyRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    @Test void disabledFeaturePreservesExistingAuthenticationWithoutNewQueries() {
        var service=new DeviceAccessPolicyService(policies,users,false);
        service.check(null); service.checkUsername("client");
        verifyNoInteractions(policies,users);
    }
    @Test void compatibilityStatusExplicitlyPreservesClientHashAndKnownExemptRoles() {
        var service=new DeviceAccessPolicyService(policies,users,false);
        for(RoleName role:RoleName.values()) {
            when(users.findByUsername("user")).thenReturn(Optional.of(user(role)));
            var status=service.status("user");
            assertThat(status.mode()).isEqualTo(role==RoleName.CLIENTE ? DeviceAccessMode.LEGACY_BROWSER_HASH : DeviceAccessMode.PASSWORD_ONLY);
            assertThat(status.enforcementEnabled()).isFalse(); assertThat(status.certificateChannelReady()).isFalse();
        }
        verifyNoInteractions(policies);
    }
    @Test void enabledCompatibilityModeAllowsClientWithoutClaimingBackendHashValidation() {
        when(policies.findByRole(5L,RoleName.CLIENTE)).thenReturn(Optional.of(DeviceAccessMode.LEGACY_BROWSER_HASH));
        new DeviceAccessPolicyService(policies,users,true).check(user(RoleName.CLIENTE));
    }
    @Test void explicitPasswordPolicyAllowsKnownRole() {
        when(policies.findByRole(5L,RoleName.CLIENTE)).thenReturn(Optional.of(DeviceAccessMode.PASSWORD_ONLY));
        new DeviceAccessPolicyService(policies,users,true).check(user(RoleName.CLIENTE));
    }
    @Test void individualPolicyIsResolvedForEverySupportedRoleWithoutFallingBackToTheRole() {
        var service=new DeviceAccessPolicyService(policies,users,true);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"individualEnabled",true);
        when(policies.findForUser(7L,5L)).thenReturn(Optional.of(DeviceAccessMode.CERTIFICATE_REQUIRED));
        for(RoleName role:RoleName.values()) {
            when(users.findByUsername("user")).thenReturn(Optional.of(user(role)));
            assertThat(service.status("user").mode()).isEqualTo(DeviceAccessMode.CERTIFICATE_REQUIRED);
        }
        verify(policies,never()).findByRole(anyLong(),any());
    }
    @Test void missingIndividualPolicyNeverFallsBackToPasswordOrHash() {
        var service=new DeviceAccessPolicyService(policies,users,true);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"individualEnabled",true);
        when(policies.findForUser(7L,5L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.check(user(RoleName.ADMINISTRATOR)))
                .isInstanceOfSatisfying(DevicePolicyException.class,e->assertThat(e.getCode()).isEqualTo(DevicePolicyException.Code.DEVICE_POLICY_NOT_CONFIGURED));
        verify(policies,never()).findByRole(anyLong(),any());
    }
    @Test void certificateRequirementNeverFallsBackToHashWhileTransportIsMissing() {
        when(policies.findByRole(5L,RoleName.CLIENTE)).thenReturn(Optional.of(DeviceAccessMode.CERTIFICATE_REQUIRED));
        assertThatThrownBy(() -> new DeviceAccessPolicyService(policies,users,true).check(user(RoleName.CLIENTE)))
                .isInstanceOfSatisfying(DevicePolicyException.class, e -> assertThat(e.getCode()).isEqualTo(DevicePolicyException.Code.DEVICE_CERTIFICATE_CHANNEL_NOT_READY));
    }
    @Test void absentRolePolicyFailsClosed() {
        when(policies.findByRole(5L,RoleName.CLIENTE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new DeviceAccessPolicyService(policies,users,true).check(user(RoleName.CLIENTE)))
                .isInstanceOfSatisfying(DevicePolicyException.class,e -> assertThat(e.getCode()).isEqualTo(DevicePolicyException.Code.DEVICE_POLICY_NOT_CONFIGURED));
    }
    @Test void roleHashMismatchDoesNotPretendTheFrontendProtectsOtherRoles() {
        when(policies.findByRole(5L,RoleName.ADMINISTRATOR)).thenReturn(Optional.of(DeviceAccessMode.LEGACY_BROWSER_HASH));
        assertThatThrownBy(() -> new DeviceAccessPolicyService(policies,users,true).check(user(RoleName.ADMINISTRATOR))).isInstanceOf(DevicePolicyException.class);
    }
    @Test void disabledDeletedOrMissingRoleUsersCannotResolveActivePolicies() {
        var service=new DeviceAccessPolicyService(policies,users,true);
        User disabled=user(RoleName.CLIENTE); disabled.setEnabled(false);
        User missingRole=user(RoleName.CLIENTE); missingRole.setRole(null);
        assertThatThrownBy(() -> service.check(disabled)).isInstanceOf(DevicePolicyException.class);
        assertThatThrownBy(() -> service.check(missingRole)).isInstanceOf(DevicePolicyException.class);
        when(users.findByUsername("deleted")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.status("deleted")).isInstanceOf(DevicePolicyException.class);
        verifyNoInteractions(policies);
    }
    @Test void unavailableDatabaseFailsClosedWithoutLeakingQueryOrCredentials() {
        when(policies.findByRole(5L,RoleName.CLIENTE)).thenThrow(new DataAccessResourceFailureException("PRIVATE_CONNECTION_DETAILS"));
        assertThatThrownBy(() -> new DeviceAccessPolicyService(policies,users,true).check(user(RoleName.CLIENTE)))
                .isInstanceOfSatisfying(DevicePolicyException.class,e -> assertThat(e.getCode()).isEqualTo(DevicePolicyException.Code.DEVICE_POLICY_UNAVAILABLE))
                .hasMessageNotContaining("PRIVATE_CONNECTION_DETAILS");
    }
    private User user(RoleName name) {
        Role role=new Role();role.setId(5L);role.setName(name);
        return User.builder().id(7L).username("user").enabled(true).role(role).build();
    }
}

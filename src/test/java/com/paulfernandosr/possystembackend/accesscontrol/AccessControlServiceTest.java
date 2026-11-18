package com.paulfernandosr.possystembackend.accesscontrol;

import com.paulfernandosr.possystembackend.accesscontrol.application.AccessControlService;
import com.paulfernandosr.possystembackend.accesscontrol.domain.*;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.time.Instant;
import java.util.UUID;
import static com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessControlServiceTest {
    private final AccessControlRepository repository=mock(AccessControlRepository.class);
    private final User user=User.builder().id(1L).username("cliente").enabled(true).build();
    private final AccessControlService service=new AccessControlService(repository,true,3,60);
    @Test void disabledModuleDoesNotReadOrMutateStorageOnAuthentication() {
        var off=new AccessControlService(repository,false,3); off.checkAccount(user);
        assertThat(off.status().enabled()).isFalse(); verifyNoInteractions(repository);
    }
    @Test void currentAccountBlockRejectsAuthentication() {
        when(repository.checkAccount(1)).thenReturn(new Decision(true,3,Instant.now().plusSeconds(60)));
        assertCode(()->service.checkAccount(user),ACCESS_ACCOUNT_BLOCKED);
    }
    @Test void unavailableStateNeverGrantsAccessWhenEnabled() {
        when(repository.checkAccount(1)).thenThrow(new DataAccessResourceFailureException("secret database address"));
        assertCode(()->service.checkAccount(user),ACCESS_CONTROL_UNAVAILABLE);
    }
    @Test void disabledUserCannotCreateAStrikeAgainstSomeoneElse() {
        user.setEnabled(false); assertCode(()->service.rejectUnauthorizedDevice(user,null,UUID.randomUUID()),ACCESS_DEVICE_NOT_AUTHORIZED);
        verifyNoInteractions(repository);
    }
    @Test void firstUnauthorizedDeviceAttemptIsDeniedBeforeThreshold() {
        UUID attempt=UUID.randomUUID();
        when(repository.recordUnauthorizedDevice(1,null,attempt,3,null,60)).thenReturn(new Decision(false,1,null));
        assertCode(()->service.rejectUnauthorizedDevice(user,null,attempt),ACCESS_DEVICE_NOT_AUTHORIZED);
        verify(repository).recordUnauthorizedDevice(1,null,attempt,3,null,60);
    }
    @Test void thresholdDecisionIsCommittedBeforeTheServiceRejects() {
        UUID attempt=UUID.randomUUID();
        when(repository.recordUnauthorizedDevice(1,null,attempt,3,null,60)).thenReturn(new Decision(true,3,Instant.now()));
        assertCode(()->service.rejectUnauthorizedDevice(user,null,attempt),ACCESS_ACCOUNT_BLOCKED);
    }
    @Test void missingTrustedAttemptOrMalformedOriginCannotCreateStrikes() {
        assertCode(()->service.rejectUnauthorizedDevice(user,null,null),ACCESS_CONTROL_INVALID_REQUEST);
        assertCode(()->service.rejectUnauthorizedDevice(user,null,UUID.randomUUID(),"untrusted-ip"),ACCESS_CONTROL_INVALID_REQUEST);
        verifyNoInteractions(repository);
    }
    @Test void administratorReasonsAreRequiredAndNormalized() {
        assertCode(()->service.unblock(1,"admin",""),ACCESS_CONTROL_INVALID_REQUEST);
        service.unblock(1,"admin"," Revisado\r\npor administrador ");
        verify(repository).unblock(1,"admin","Revisado  por administrador");
    }
    @Test void pageBoundsQuotaAndMonthDurationAreValidatedBeforeStorage() {
        assertCode(()->service.users("",0,1000),ACCESS_CONTROL_INVALID_REQUEST);
        assertCode(()->service.setQuota(1,0,"admin","Revision administrativa"),ACCESS_CONTROL_INVALID_REQUEST);
        assertCode(()->service.setBlockDays(0,"admin","Revision administrativa"),ACCESS_CONTROL_INVALID_REQUEST);
        assertCode(()->service.setBlockDays(3651,"admin","Revision administrativa"),ACCESS_CONTROL_INVALID_REQUEST);
        verifyNoInteractions(repository);
    }
    @Test void settingsAndQuotaAreIndependentOfHistoryAndExistingBlocks() {
        service.setBlockDays(2,"admin","Nueva duracion autorizada"); service.setQuota(1,2,"admin","Segunda computadora aprobada");
        verify(repository).setBlockDays(2,"admin","Nueva duracion autorizada");
        verify(repository).setQuota(1,2,"admin","Segunda computadora aprobada");
        verify(repository,never()).unblock(anyLong(),anyString(),anyString());
    }
    @Test void invalidThresholdOrCooldownCannotStartASilentWeakPolicy() {
        assertThatIllegalArgumentException().isThrownBy(()->new AccessControlService(repository,true,0));
        assertThatIllegalArgumentException().isThrownBy(()->new AccessControlService(repository,true,3,0));
    }
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,AccessControlException.Code code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AccessControlException.class,e->assertThat(e.getCode()).isEqualTo(code));
    }
}

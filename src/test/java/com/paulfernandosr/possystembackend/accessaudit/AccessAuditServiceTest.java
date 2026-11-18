package com.paulfernandosr.possystembackend.accessaudit;

import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import com.paulfernandosr.possystembackend.accessaudit.domain.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.port.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessAuditServiceTest {
    private final AccessAuditRepository repository=mock(AccessAuditRepository.class);
    private final Metadata meta=new Metadata("127.0.0.1","Test",UUID.randomUUID());
    private final AccessAuditService service=new AccessAuditService(repository,()->meta,true,(key,user)->{});
    private AuditQuery query() { return AuditQuery.of(LocalDate.of(2026,10,29),LocalDate.of(2026,10,29),null,null,null,null,0,25); }
    @Test void dayUsesLimaBoundariesNotUtcMidnight() {
        assertThat(query().from()).isEqualTo(Instant.parse("2026-10-29T05:00:00Z"));
        assertThat(query().until()).isEqualTo(Instant.parse("2026-10-30T05:00:00Z"));
    }
    @Test void rejectsUnboundedReversedOrUnknownFilters() {
        LocalDate day=LocalDate.of(2026,10,29);
        assertThatThrownBy(()->AuditQuery.of(day,day.plusDays(31),null,null,null,null,0,25)).isInstanceOf(AuditException.class);
        assertThatThrownBy(()->AuditQuery.of(day,day.minusDays(1),null,null,null,null,0,25)).isInstanceOf(AuditException.class);
        assertThatThrownBy(()->AuditQuery.of(day,day,null,null,"ANY_SQL",null,0,25)).isInstanceOf(AuditException.class);
        assertThatThrownBy(()->AuditQuery.of(day,day,null,null,null,null,0,1000)).isInstanceOf(AuditException.class);
    }
    @Test void disabledCaptureDoesNotTouchTheDatabaseAndQueriesCannotPretendThereAreNoEvents() {
        var disabled=new AccessAuditService(repository,()->meta,false,(key,user)->{});
        assertThat(disabled.status().enabled()).isFalse();
        disabled.loginRejected("attempt",new RuntimeException("secret"));
        assertThatThrownBy(()->disabled.summary(query())).isInstanceOf(AuditException.class);
        verifyNoInteractions(repository);
    }
    @Test void rejectedLoginIsNotPresentedAsVerifiedIdentityAndNeverIncludesTheExceptionMessage() {
        service.loginRejected("unknown",new RuntimeException("PASSWORD_AND_JWT_MUST_NOT_APPEAR"));
        verify(repository).record("LOGIN_REJECTED","REJECTED","BACKEND",null,"unknown",null,null,"AUTHENTICATION_UNAVAILABLE",meta,true);
    }
    @Test void browserReportsRemainExplicitlyUntrustedAndNeverGrantSessionAccess() {
        service.browser("user","key",true);
        verify(repository).record("BROWSER_COMPLETED","INFO","BROWSER","user",null,"key",null,"CLIENT_DECLARED_VALIDATION",meta,true);
        verifyNoMoreInteractions(repository);
    }
    @Test void categoryFilterIsBoundedAndCannotBecomeCallerSelectedSql() {
        var day=LocalDate.of(2026,11,16);
        assertThat(AuditQuery.of(day,day,null,null,null,null,0,25,"CERTIFICATE").category()).isEqualTo("CERTIFICATE");
        assertThatThrownBy(()->AuditQuery.of(day,day,null,null,null,null,0,25,"CERTIFICATE' OR TRUE--")).isInstanceOf(AuditException.class);
    }
    @Test void certificateFailureIsSpecificButAThirdStrikeRemainsAnAccountBlock() {
        var evidence=new Metadata(meta.peerAddress(),meta.userAgent(),meta.correlationId(),null,
                new AuthenticationContext("NONE",null,null,null,null,"CERTIFICATE_MISSING"));
        var detailed=new AccessAuditService(repository,()->evidence,true,(key,user)->{});
        detailed.loginRejected("LENIN",new com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException(
                com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.ACCESS_DEVICE_NOT_AUTHORIZED));
        verify(repository).record("LOGIN_REJECTED","REJECTED","BACKEND",null,"LENIN",null,null,"CERTIFICATE_MISSING",evidence,true);
        detailed.loginRejected("LENIN",new com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException(
                com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.ACCESS_ACCOUNT_BLOCKED));
        verify(repository).record("LOGIN_REJECTED","REJECTED","BACKEND",null,"LENIN",null,null,"ACCESS_ACCOUNT_BLOCKED",evidence,true);
    }
    @Test void aServiceFailureCannotBeMisreportedAsThePreviouslyObservedMissingCertificate() {
        var evidence=new Metadata(meta.peerAddress(),meta.userAgent(),meta.correlationId(),null,
                new AuthenticationContext("NONE",null,null,null,null,"CERTIFICATE_MISSING"));
        var detailed=new AccessAuditService(repository,()->evidence,true,(key,user)->{});
        detailed.loginRejected("LENIN",new com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException(
                com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.ACCESS_CONTROL_UNAVAILABLE));
        verify(repository).record("LOGIN_REJECTED","REJECTED","BACKEND",null,"LENIN",null,null,"ACCESS_CONTROL_UNAVAILABLE",evidence,true);
    }
    @Test void exportRefusesTruncationAndDoesNotRecordSuccess() {
        when(repository.events(query(),5001,0)).thenReturn(new Page<>(List.of(),5001,0,25));
        assertThatThrownBy(()->service.export(query(),"admin")).isInstanceOfSatisfying(AuditException.class,e->assertThat(e.status()).isEqualTo(409));
        verify(repository,never()).record(any(),any(),any(),any(),any(),any(),any(),any(),any(),anyBoolean());
    }
    @Test void csvNeutralizesFormulasAfterLeadingWhitespaceAndEscapesQuotes() {
        assertThat(AccessAuditService.csvCell("  =HYPERLINK(\"bad\")")).isEqualTo("\"'  =HYPERLINK(\"\"bad\"\")\"");
        assertThat(AccessAuditService.csvCell("\t+cmd")).startsWith("\"'");
        assertThat(AccessAuditService.csvCell(null)).isEqualTo("\"\"");
    }
    @Test void revocationRequiresAnExplicitBoundedReason() {
        assertThatThrownBy(()->service.revoke(UUID.randomUUID(),"admin","short")).isInstanceOf(AuditException.class);
        assertThatThrownBy(()->service.revoke(UUID.randomUUID(),"admin","a".repeat(301))).isInstanceOf(AuditException.class);
        verifyNoInteractions(repository);
    }
    @Test void highVolumeDenialsKeepCountsButDoNotCreateUnlimitedAccountDetail() {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"denialDetailsPerMinute",1);
        service.loginRejected("first",new IllegalArgumentException());service.loginRejected("second",new IllegalArgumentException());
        verify(repository).record(eq("LOGIN_REJECTED"),eq("REJECTED"),eq("BACKEND"),isNull(),isNull(),isNull(),isNull(),
                eq("HIGH_VOLUME_DENIAL_SUMMARY"),argThat(m->m.peerAddress()==null && m.userAgent()==null),eq(true));
    }
}

package com.paulfernandosr.possystembackend.community.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository;
import com.paulfernandosr.possystembackend.trustedclient.ControlSessionGuard;
import org.junit.jupiter.api.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunityNotificationSseServiceTest {
    private final SessionRepository sessions=mock(SessionRepository.class);
    private final ControlSessionGuard guard=mock(ControlSessionGuard.class);
    private final UserDetailsService users=mock(UserDetailsService.class);
    private final CommunityNotificationSseService service=new CommunityNotificationSseService(sessions,guard,users);
    private final String sid="a".repeat(64);
    @BeforeEach void setup() {
        when(users.loadUserByUsername("user")).thenReturn(User.withUsername("user").password("unused").authorities("READ").build());
        when(sessions.isActive(sid,"user")).thenReturn(true);when(guard.active(sid,"user",false)).thenReturn(true);
    }
    private Map<?,?> subscriptions(){return (Map<?,?>) ReflectionTestUtils.getField(service,"emitters");}
    @Test void revokedSessionClosesIdleStreamAtRevalidation() {
        service.subscribe(1L,"user",sid,Instant.now().plusSeconds(300));assertThat(subscriptions()).hasSize(1);
        when(sessions.isActive(sid,"user")).thenReturn(false);service.revalidate();assertThat(subscriptions()).isEmpty();
    }
    @Test void oldCycleOrRevokedPcClosesStreamWithoutWaitingForAnotherBrowserRequest() {
        service.subscribe(1L,"user",sid,Instant.now().plusSeconds(300));when(guard.active(sid,"user",false)).thenReturn(false);
        service.revalidate();assertThat(subscriptions()).isEmpty();
    }
    @Test void userDisableClosesStreamAndReenableDoesNotRecreateIt() {
        service.subscribe(1L,"user",sid,Instant.now().plusSeconds(300));when(users.loadUserByUsername("user")).thenReturn(User.withUsername("user").password("unused").disabled(true).authorities("READ").build());
        service.revalidate();assertThat(subscriptions()).isEmpty();
        when(users.loadUserByUsername("user")).thenReturn(User.withUsername("user").password("unused").authorities("READ").build());service.revalidate();assertThat(subscriptions()).isEmpty();
    }
    @Test void expiredTokenCannotOpenStream() {
        assertThatThrownBy(()->service.subscribe(1L,"user",sid,Instant.now().minusSeconds(1))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(subscriptions()).isEmpty();
    }
    @Test void storageFailureClosesStreamInsteadOfContinuingToDiscloseMessages() {
        service.subscribe(1L,"user",sid,Instant.now().plusSeconds(300));when(sessions.isActive(sid,"user")).thenThrow(new IllegalStateException("unavailable"));
        service.revalidate();assertThat(subscriptions()).isEmpty();
    }
    @Test void pushRevalidatesBeforeSendingNotification() {
        service.subscribe(1L,"user",sid,Instant.now().plusSeconds(300));when(guard.active(sid,"user",false)).thenReturn(false);
        service.push(1L,null);assertThat(subscriptions()).isEmpty();
    }
}

package com.paulfernandosr.possystembackend.timeactivity;

import com.paulfernandosr.possystembackend.timeactivity.application.TimeActivityService;
import com.paulfernandosr.possystembackend.timeactivity.application.TimeActivityService.Identity;
import com.paulfernandosr.possystembackend.timeactivity.application.TimeActivityService.Query;
import com.paulfernandosr.possystembackend.timeactivity.domain.ActivityIntervals;
import com.paulfernandosr.possystembackend.timeactivity.domain.TimeActivity.*;
import com.paulfernandosr.possystembackend.timeactivity.infrastructure.TimeActivityConfiguration;
import com.paulfernandosr.possystembackend.timeactivity.infrastructure.TimeActivityController;
import com.paulfernandosr.possystembackend.timeactivity.domain.port.TimeActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimeActivityPilotTest {
    @Test
    void defaultsToNoModuleBeans() {
        new ApplicationContextRunner().withUserConfiguration(TimeActivityConfiguration.class, TimeActivityController.class)
                .run(context -> {
                    assertFalse(context.containsBean("timeActivityService"));
                    assertTrue(context.getBeansOfType(TimeActivityController.class).isEmpty());
                });
    }

    @Test
    void appliesTheThresholdAndDeduplicatesTwoTabs() {
        long start = Instant.parse("2026-11-17T14:00:00Z").toEpochMilli();
        var segments = List.of(
                new Segment(UUID.randomUUID(), 1, "ana", "CASHIER", "/sales", start, start + 1_020_000, start, 600_000),
                new Segment(UUID.randomUUID(), 1, "ana", "CASHIER", "/products", start + 300_000, start + 400_000, null, 600_000));
        var timeline = ActivityIntervals.timeline(ActivityIntervals.calculate(segments, start, start + 1_020_000), null);
        assertEquals(List.of(new Interval("/sales", start, start + 600_000, State.RECENT),
                new Interval("/sales", start + 600_000, start + 1_020_000, State.QUIET)), timeline);
    }

    @Test
    void clipsAtLocalDayBoundaryAndKeepsUncoveredGaps() {
        long start = Instant.parse("2026-11-18T05:00:00Z").toEpochMilli();
        var segments = List.of(
                new Segment(UUID.randomUUID(), 1, "ana", "CASHIER", "/sales", start - 5_000, start + 5_000, null, 600_000),
                new Segment(UUID.randomUUID(), 1, "ana", "CASHIER", "/sales", start + 10_000, start + 15_000, null, 600_000));
        var timeline = ActivityIntervals.timeline(ActivityIntervals.calculate(segments, start, start + 30_000), null);
        assertEquals(List.of(new Interval("/sales", start, start + 5_000, State.UNCLASSIFIED),
                new Interval("", start + 5_000, start + 10_000, State.GAP),
                new Interval("/sales", start + 10_000, start + 15_000, State.UNCLASSIFIED)), timeline);
    }

    @Test
    void aRequestCannotReadAnUnassignedTeamMember() {
        TimeActivityRepository store = mock(TimeActivityRepository.class);
        when(store.actor("gestor")).thenReturn(Optional.of(new Actor(1, "gestor", "ADMINISTRATOR", null)));
        when(store.team(1)).thenReturn(List.of(2L));
        var service = new TimeActivityService(store, 10, 1, false);
        var identity = new Identity("gestor", "session-a", Set.of("VIEW_TIME_SELF", "VIEW_TIME_TEAM"));
        var query = new Query(LocalDate.now(ZoneId.of("America/Lima")), "team", 3L, null, null);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.summary(identity, query)).getStatusCode());
        verify(store, never()).observations(anyList(), anyLong(), anyLong());
    }

    @Test
    void staleContextCannotIngestForANewSessionOfTheSameUser() {
        TimeActivityRepository store = mock(TimeActivityRepository.class);
        when(store.actor("ana")).thenReturn(Optional.of(new Actor(1, "ana", "CASHIER", Instant.EPOCH)));
        var service = new TimeActivityService(store, 10, 1, false);
        String old = service.context(new Identity("ana", "session-a", Set.of("MANAGE_SALES"))).contextId();
        var newSession = new Identity("ana", "session-b", Set.of("MANAGE_SALES"));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.ingest(newSession, new Batch(old, 1, List.of()))).getStatusCode());
        verify(store, never()).save(any(), any(), anyInt(), anyInt());
    }

    @Test
    void repeatsAnObservationWithoutInflatingTheCount() {
        TimeActivityRepository store = mock(TimeActivityRepository.class);
        when(store.actor("ana")).thenReturn(Optional.of(new Actor(1, "ana", "CASHIER", Instant.EPOCH)));
        when(store.save(any(), any(), eq(10), eq(1))).thenReturn(true, false);
        var service = new TimeActivityService(store, 10, 1, false);
        var identity = new Identity("ana", "session-a", Set.of("MANAGE_SALES"));
        long now = System.currentTimeMillis();
        var o = new Observation(UUID.randomUUID(), "/sales", now - 2_000, now - 1_000, null);
        var batch = new Batch(service.context(identity).contextId(), 1, List.of(o));
        assertEquals(new BatchReceipt(1, 0), service.ingest(identity, batch));
        assertEquals(new BatchReceipt(0, 1), service.ingest(identity, batch));
    }
    @Test void enablingTheModuleCreatesOnlyItsOwnBeansAndDoesNotQueryTheDatabaseAtStartup() {
        var jdbc=mock(org.springframework.jdbc.core.simple.JdbcClient.class);
        new ApplicationContextRunner().withUserConfiguration(TimeActivityConfiguration.class,TimeActivityController.class)
                .withBean(org.springframework.jdbc.core.simple.JdbcClient.class,()->jdbc)
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class,com.fasterxml.jackson.databind.ObjectMapper::new)
                .withPropertyValues("imbasac.time-activity.enabled=true")
                .run(context->{
                    assertNotNull(context.getBean(TimeActivityService.class));
                    assertEquals(1,context.getBeansOfType(TimeActivityController.class).size());
                    verifyNoInteractions(jdbc);
                });
    }
    @Test void documentedEnvironmentFlagActivatesTheOptInWithoutChangingApplicationProfiles() {
        var jdbc=mock(org.springframework.jdbc.core.simple.JdbcClient.class);
        new ApplicationContextRunner().withUserConfiguration(TimeActivityConfiguration.class,TimeActivityController.class)
                .withBean(org.springframework.jdbc.core.simple.JdbcClient.class,()->jdbc)
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class,com.fasterxml.jackson.databind.ObjectMapper::new)
                .withInitializer(context->context.getEnvironment().getPropertySources().addFirst(
                        new org.springframework.core.env.SystemEnvironmentPropertySource("pilot-env",java.util.Map.of("IMBASAC_TIME_ACTIVITY_ENABLED","true"))))
                .run(context->{ assertNotNull(context.getBean(TimeActivityService.class)); verifyNoInteractions(jdbc); });
    }
    @Test void anObservationCannotClaimAModuleOutsideTheUsersCurrentPermissions() {
        var store=mock(TimeActivityRepository.class);
        when(store.actor("ana")).thenReturn(Optional.of(new Actor(1,"ana","CASHIER",Instant.EPOCH)));
        var service=new TimeActivityService(store,10,1,false);
        var identity=new Identity("ana","session-a",Set.of("VIEW_TIME_SELF"));
        long now=System.currentTimeMillis();
        var input=new Batch(service.context(identity).contextId(),1,List.of(new Observation(UUID.randomUUID(),"/sales",now-2000,now-1000,null)));
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.ingest(identity,input)).getStatusCode());
        verify(store,never()).save(any(),any(),anyInt(),anyInt());
    }
    @Test void filteringOneModuleDoesNotReintroduceTheTimeOverlappedByAnotherTab() {
        var store=mock(TimeActivityRepository.class);
        when(store.actor("ana")).thenReturn(Optional.of(new Actor(1,"ana","CASHIER",Instant.EPOCH)));
        long start=System.currentTimeMillis()-60000;
        when(store.observations(anyList(),anyLong(),anyLong())).thenReturn(List.of(
                new Segment(UUID.randomUUID(),1,"ana","CASHIER","/sales",start,start+30000,start,600000),
                new Segment(UUID.randomUUID(),1,"ana","CASHIER","/products",start+10000,start+20000,start+10000,600000)));
        var service=new TimeActivityService(store,10,1,false);
        var result=service.summary(new Identity("ana","session-a",Set.of("VIEW_TIME_SELF")),
                new Query(LocalDate.now(ZoneId.of("America/Lima")),"self",null,"/sales",null));
        assertEquals(1,result.rows().size()); assertEquals(20000,result.rows().getFirst().recentMs());
        verify(store,never()).hasSaleJournal(); verify(store,never()).hasProformas();
    }
}

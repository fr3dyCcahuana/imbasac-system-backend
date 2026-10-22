package com.paulfernandosr.possystembackend.driverlicense.assisted;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static com.paulfernandosr.possystembackend.driverlicense.assisted.MtcAssistedDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MtcAssistedServiceTest {
    MtcAssistedProperties properties;
    MtcAssistedBrowserFactory factory;
    MtcAssistedBrowser browser;
    MtcAssistedService service;

    @BeforeEach void setup() {
        properties = new MtcAssistedProperties(); properties.setEnabled(true); properties.setMaxSessions(1);
        factory = mock(MtcAssistedBrowserFactory.class); browser = mock(MtcAssistedBrowser.class);
        when(factory.create()).thenReturn(browser);
        when(browser.open("DNI", "00000000")).thenReturn(new MtcAssistedBrowser.ChallengeView("Consigna de prueba", List.of(new Option(0, "image"), new Option(1, "image"))));
        service = new MtcAssistedService(properties, factory, new MtcAssistedParser());
        when(browser.submit(anyInt(), anyBoolean())).thenReturn(observation("00000000", "Puntos acumulados\n5 puntos"));
    }
    @AfterEach void cleanup() { service.shutdown(); }

    @Test void forwardsOnlyHumanChoiceAndReturnsResultOnTheSessionWorker() {
        var threads = new CopyOnWriteArrayList<Long>();
        when(factory.create()).thenAnswer(i -> { threads.add(Thread.currentThread().threadId()); return browser; });
        when(browser.submit(1, true)).thenAnswer(i -> { threads.add(Thread.currentThread().threadId()); return observation("00000000", "Puntos acumulados\n5 puntos"); });
        var challenge = service.start("operator", new StartRequest("DNI", "00000000"));
        assertTrue(challenge.expiresAt().isAfter(java.time.Instant.now()));
        var result = service.answer("operator", challenge.sessionId(), new AnswerRequest(challenge.challengeId(), 1, true));
        assertTrue(result.complete()); assertEquals(5, result.accumulatedPoints());
        assertEquals(1, threads.stream().distinct().count());
        verify(browser).submit(1, true);
        assertCode("MTC_SESSION_NOT_FOUND", () -> service.answer("operator", challenge.sessionId(), new AnswerRequest(challenge.challengeId(), 1, true)));
    }

    @Test void rejectsForeignSessionStaleChallengeAndMissingConsentBeforeClick() {
        var c = service.start("operator", new StartRequest("DNI", "00000000"));
        assertCode("MTC_SESSION_NOT_FOUND", () -> service.answer("other", c.sessionId(), new AnswerRequest(c.challengeId(), 1, true)));
        assertCode("MTC_CHALLENGE_CHANGED", () -> service.answer("operator", c.sessionId(), new AnswerRequest("old", 1, true)));
        assertCode("MTC_SELECTION_REQUIRED", () -> service.answer("operator", c.sessionId(), new AnswerRequest(c.challengeId(), 1, false)));
        assertCode("MTC_SELECTION_REQUIRED", () -> service.answer("operator", c.sessionId(), new AnswerRequest(c.challengeId(), 9, true)));
        verify(browser, never()).submit(anyInt(), anyBoolean());
    }

    @Test void invalidChoiceClosesSessionSoItCannotBeRetried() {
        var c = service.start("operator", new StartRequest("DNI", "00000000"));
        when(browser.submit(0, true)).thenThrow(new MtcAssistedException(422, "MTC_CAPTCHA_INVALIDO", "Invalid"));
        assertCode("MTC_CAPTCHA_INVALIDO", () -> service.answer("operator", c.sessionId(), new AnswerRequest(c.challengeId(), 0, true)));
        assertCode("MTC_SESSION_NOT_FOUND", () -> service.answer("operator", c.sessionId(), new AnswerRequest(c.challengeId(), 0, true)));
        verify(browser, times(1)).submit(0, true);
    }

    @Test void retriesOnlyInitialTransientLoadOnceInAFreshBrowserOnTheSameWorker() {
        var secondBrowser = mock(MtcAssistedBrowser.class);
        var threads = new CopyOnWriteArrayList<Long>();
        when(factory.create()).thenAnswer(i -> { threads.add(Thread.currentThread().threadId()); return browser; })
                .thenAnswer(i -> { threads.add(Thread.currentThread().threadId()); return secondBrowser; });
        when(browser.open("DNI", "00000000")).thenThrow(new MtcAssistedException(503, "MTC_CHALLENGE_LOAD_FAILED", "Temporary failure"));
        doAnswer(i -> { threads.add(Thread.currentThread().threadId()); return null; }).when(browser).close();
        when(secondBrowser.open("DNI", "00000000")).thenReturn(new MtcAssistedBrowser.ChallengeView("Test", List.of(new Option(0, "image"), new Option(1, "image"))));
        var challenge = service.start("operator", new StartRequest("DNI", "00000000"));
        assertEquals(2, challenge.options().size());
        assertEquals(1, threads.stream().distinct().count());
        verify(browser).close();
        verify(factory, times(2)).create();
        verify(secondBrowser, never()).submit(anyInt(), anyBoolean());
    }

    @Test void stopsAfterOneRetry() {
        when(browser.open("DNI", "00000000")).thenThrow(new MtcAssistedException(503, "MTC_CHALLENGE_LOAD_FAILED", "Temporary failure"));
        assertCode("MTC_CHALLENGE_LOAD_FAILED", () -> service.start("operator", new StartRequest("DNI", "00000000")));
        verify(factory, times(2)).create();
    }

    @Test void changedChallengeFormatIsNotRetried() {
        when(browser.open("DNI", "00000000")).thenThrow(new MtcAssistedException(502, "MTC_CHALLENGE_CHANGED", "Changed"));
        assertCode("MTC_CHALLENGE_CHANGED", () -> service.start("operator", new StartRequest("DNI", "00000000")));
        verify(factory, times(1)).create();
    }

    @Test void missingPointsRemainUnknownAndResultIsIncomplete() {
        when(browser.submit(1, true)).thenReturn(observation("00000000", "Puntos acumulados\nCargando…"));
        var c = service.start("operator", new StartRequest("DNI", "00000000"));
        var result = service.answer("operator", c.sessionId(), new AnswerRequest(c.challengeId(), 1, true));
        assertNull(result.accumulatedPoints()); assertFalse(result.complete()); assertFalse(result.warnings().isEmpty());
    }

    @Test void mismatchedDocumentIsRejectedAndClosesTheSession() {
        when(browser.submit(1, true)).thenReturn(observation("99999999", "Puntos acumulados\n0 puntos"));
        var c = service.start("operator", new StartRequest("DNI", "00000000"));
        assertCode("MTC_DOCUMENT_MISMATCH", () -> service.answer("operator", c.sessionId(), new AnswerRequest(c.challengeId(), 1, true)));
        assertCode("MTC_SESSION_NOT_FOUND", () -> service.answer("operator", c.sessionId(), new AnswerRequest(c.challengeId(), 1, true)));
    }

    @Test void limitsBrowserCapacityAndValidatesDocumentBeforeBrowserCreation() {
        assertCode("MTC_INVALID_DOCUMENT", () -> service.start("operator", new StartRequest("DNI", "123")));
        verify(factory, never()).create();
        service.start("operator", new StartRequest("DNI", "00000000"));
        assertCode("MTC_SESSION_EXISTS", () -> service.start("operator", new StartRequest("DNI", "00000000")));
        assertCode("MTC_CAPACITY", () -> service.start("other", new StartRequest("DNI", "00000000")));
    }

    @Test void disabledByDefaultDoesNotOpenBrowser() {
        properties.setEnabled(false);
        assertCode("MTC_PILOT_DISABLED", () -> service.start("operator", new StartRequest("DNI", "00000000")));
        verify(factory, never()).create();
    }

    @Test void expiredSessionIsClosedBeforeAnySelectionIsForwarded() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.now());
        var clock = new java.time.Clock() {
            @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public java.time.Instant instant() { return now.get(); }
        };
        service = new MtcAssistedService(properties, factory, new MtcAssistedParser(), clock);
        var closed = new java.util.concurrent.CountDownLatch(1);
        doAnswer(i -> { closed.countDown(); return null; }).when(browser).close();
        var c = service.start("operator", new StartRequest("DNI", "00000000"));
        now.set(c.expiresAt());
        assertCode("MTC_SESSION_EXPIRED", () -> service.answer("operator", c.sessionId(), new AnswerRequest(c.challengeId(), 1, true)));
        assertTrue(closed.await(2, java.util.concurrent.TimeUnit.SECONDS));
        verify(browser, never()).submit(anyInt(), anyBoolean());
    }

    static MtcAssistedBrowser.Observation observation(String document, String points) {
        return new MtcAssistedBrowser.Observation(Map.of("Número de documento", document, "Tipo de documento", "DNI", "Nombres", "PERSONA", "Apellidos", "DE PRUEBA"),
                List.of(Map.of("Número de licencia", "TEST00000000", "Clase y categoría", "A I", "Vigente hasta", "17/11/2033", "__text", "Licencia: A I - Vigente")),
                points, List.of());
    }
    static void assertCode(String code, Runnable action) { assertEquals(code, assertThrows(MtcAssistedException.class, action::run).code()); }
}

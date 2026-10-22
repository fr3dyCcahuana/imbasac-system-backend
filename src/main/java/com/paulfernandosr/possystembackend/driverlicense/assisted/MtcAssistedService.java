package com.paulfernandosr.possystembackend.driverlicense.assisted;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.paulfernandosr.possystembackend.driverlicense.assisted.MtcAssistedDtos.*;

@Service
public class MtcAssistedService {
    private static final Logger log = LoggerFactory.getLogger(MtcAssistedService.class);
    private final MtcAssistedProperties properties;
    private final MtcAssistedBrowserFactory factory;
    private final MtcAssistedParser parser;
    private final Clock clock;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Semaphore capacity;

    @org.springframework.beans.factory.annotation.Autowired
    public MtcAssistedService(MtcAssistedProperties properties, MtcAssistedBrowserFactory factory,
                           MtcAssistedParser parser) {
        this(properties, factory, parser, Clock.systemUTC());
    }
    MtcAssistedService(MtcAssistedProperties properties, MtcAssistedBrowserFactory factory,
                    MtcAssistedParser parser, Clock clock) {
        this.properties = properties;
        this.factory = factory;
        this.parser = parser;
        this.clock = clock;
        capacity = new Semaphore(Math.max(1, properties.getMaxSessions()));
    }

    public Status status() {
        return new Status(properties.isEnabled(), "https://licencias.mtc.gob.pe/assets/PoliticaPrivacidad.pdf", Math.max(30, properties.getSessionTtlSeconds()));
    }

    public Challenge start(String owner, StartRequest request) {
        requireEnabled();
        String type = request.documentType() == null ? "" : request.documentType().trim().toUpperCase(Locale.ROOT);
        String document = request.documentNumber() == null ? "" : request.documentNumber().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DNI", "CE", "CS", "PAS", "TI", "CPP").contains(type)
                || !(type.equals("DNI") ? document.matches("[0-9]{8}") : document.matches("[A-Z0-9]{4,15}"))) {
            throw new MtcAssistedException(400, "MTC_INVALID_DOCUMENT", "Revisa el tipo y número de documento. El DNI debe tener 8 dígitos.");
        }
        Session session;
        synchronized (sessions) {
            if (sessions.values().stream().anyMatch(s -> s.owner.equals(owner))) {
                throw new MtcAssistedException(409, "MTC_SESSION_EXISTS", "Ya tienes una consulta abierta. Ciérrala o espera su vencimiento.");
            }
            if (!capacity.tryAcquire()) throw new MtcAssistedException(429, "MTC_CAPACITY", "Las consultas del MTC están ocupadas. Intenta nuevamente en unos momentos.");
            session = new Session(owner, type, document);
            sessions.put(session.id, session);
        }
        session.busy.set(true);
        try {
            MtcAssistedBrowser.ChallengeView view = call(session, () -> openChallenge(session));
            session.optionCount = view.options().size();
            session.expiresAt = clock.instant().plusSeconds(Math.max(30, properties.getSessionTtlSeconds()));
            return new Challenge(session.id, session.challengeId, type, document, view.question(), view.options(), session.expiresAt);
        } catch (RuntimeException e) { close(session); throw e; }
        finally { session.busy.set(false); }
    }

    public Result answer(String owner, String id, AnswerRequest answer) {
        requireEnabled();
        Session session = owned(owner, id);
        if (!clock.instant().isBefore(session.expiresAt)) {
            close(session);
            throw new MtcAssistedException(410, "MTC_SESSION_EXPIRED", "La consulta venció. Genera un nuevo desafío.");
        }
        if (!Objects.equals(session.challengeId, answer.challengeId())) {
            throw new MtcAssistedException(409, "MTC_CHALLENGE_CHANGED", "El desafío corresponde a otra consulta.");
        }
        if (!answer.privacyAccepted() || answer.imageIndex() == null || answer.imageIndex() < 0 || answer.imageIndex() >= session.optionCount) {
            throw new MtcAssistedException(400, "MTC_SELECTION_REQUIRED", "Elige una imagen y acepta expresamente la política del MTC.");
        }
        if (!session.busy.compareAndSet(false, true)) throw new MtcAssistedException(409, "MTC_SESSION_BUSY", "La consulta ya está en proceso.");
        try {
            return call(session, () -> {
                var observation = session.browser.submit(answer.imageIndex(), answer.privacyAccepted());
                String returnedDocument = parser.field(observation.identity(), "Número de documento", "Nro. de documento de identidad");
                if (!session.document.equalsIgnoreCase(returnedDocument)) {
                    throw new MtcAssistedException(502, "MTC_DOCUMENT_MISMATCH", "El documento del resultado no coincide con el consultado. El resultado no se utilizó.");
                }
                String names = parser.field(observation.identity(), "Nombres");
                String surnames = parser.field(observation.identity(), "Apellidos");
                String returnedType = parser.field(observation.identity(), "Tipo de documento");
                boolean typeMatches = matchesDocumentType(session.documentType, returnedType);
                var unique = new LinkedHashMap<String, License>();
                for (var fields : observation.licenses()) {
                    License license = parser.license(fields);
                    if (parser.meaningful(license.number())) unique.putIfAbsent(license.number(), license);
                }
                List<License> licenses = List.copyOf(unique.values());
                Integer points = parser.points(observation.pointsText());
                List<String> warnings = new ArrayList<>(observation.warnings());
                if (licenses.isEmpty()) warnings.add("No se pudo extraer una licencia. Revisa el resultado en el portal; esto no confirma ausencia de licencia.");
                if (licenses.stream().anyMatch(l -> !parser.completeLicense(l))) warnings.add("Alguna licencia tiene datos incompletos o un estado no reconocido.");
                if (points == null) warnings.add("Los puntos son desconocidos; no equivalen a cero.");
                if (names == null || surnames == null) warnings.add("No se pudo leer el nombre completo del conductor.");
                if (!typeMatches) warnings.add("No se pudo confirmar el tipo de documento devuelto por el MTC.");
                boolean complete = !licenses.isEmpty() && licenses.stream().allMatch(parser::completeLicense)
                        && points != null && names != null && surnames != null && typeMatches;
                return new Result(session.documentType, returnedDocument, names, surnames,
                        licenses, points, complete, properties.getSearchUrl(), clock.instant(), List.copyOf(warnings));
            });
        } finally { close(session); }
    }

    public void cancel(String owner, String id) {
        requireEnabled();
        Session session = sessions.get(id);
        if (session != null) close(owned(owner, id));
    }
    private Session owned(String owner, String id) {
        Session session = sessions.get(id);
        if (session == null || !Objects.equals(owner, session.owner)) {
            throw new MtcAssistedException(404, "MTC_SESSION_NOT_FOUND", "La consulta no está disponible. Genera un nuevo desafío.");
        }
        return session;
    }
    private void requireEnabled() {
        if (!properties.isEnabled()) throw new MtcAssistedException(404, "MTC_PILOT_DISABLED", "La consulta del MTC no está habilitada en este servidor.");
    }
    private MtcAssistedBrowser.ChallengeView openChallenge(Session session) {
        for (int attempt = 0; attempt < 2; attempt++) {
            session.browser = factory.create();
            try { return session.browser.open(session.documentType, session.document); }
            catch (MtcAssistedException error) {
                if (attempt != 0 || !"MTC_CHALLENGE_LOAD_FAILED".equals(error.code())) throw error;
                // No answer or search has been sent. Reopen once in a fresh browser on this worker.
                session.browser.close();
                session.browser = null;
                log.info("Retrying initial MTC challenge load after a connection failure");
            }
        }
        throw new IllegalStateException("Unreachable challenge attempt");
    }
    private boolean matchesDocumentType(String requested, String returned) {
        String normalized = parser.normalize(returned);
        return switch (requested) {
            case "DNI" -> Set.of("DNI", "DOCUMENTONACIONALDEIDENTIDAD").contains(normalized);
            case "CE" -> Set.of("CE", "CARNEDEEXTRANJERIA", "CARNETDEEXTRANJERIA").contains(normalized);
            case "CS" -> Set.of("CS", "CARNEDESOLICITANTEDEREFUGIO").contains(normalized);
            case "PAS" -> Set.of("PAS", "PASAPORTE").contains(normalized);
            case "TI" -> Set.of("TI", "TARJETADEIDENTIDAD").contains(normalized);
            case "CPP" -> Set.of("CPP", "PTP", "PTPCPP").contains(normalized);
            default -> false;
        };
    }
    private <T> T call(Session session, Callable<T> work) {
        try { return session.worker.submit(work).get(100, TimeUnit.SECONDS); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof MtcAssistedException error) throw error;
            throw new MtcAssistedException(502, "MTC_PILOT_UNAVAILABLE", "No se pudo completar la operación en el portal MTC. Genera otra consulta o revisa la conexión del servidor.");
        } catch (TimeoutException e) {
            throw new MtcAssistedException(504, "MTC_PILOT_TIMEOUT", "El MTC tardó demasiado en responder. Genera una nueva consulta.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MtcAssistedException(503, "MTC_PILOT_INTERRUPTED", "La consulta fue interrumpida.");
        }
    }
    private void close(Session session) {
        if (!session.closed.compareAndSet(false, true)) return;
        sessions.remove(session.id, session);
        // All creation, use and destruction happen on the same Playwright worker.
        session.worker.submit(() -> {
            try { if (session.browser != null) session.browser.close(); }
            finally { capacity.release(); }
        });
        session.worker.shutdown();
    }
    @Scheduled(fixedDelay = 15000) public void cleanup() {
        sessions.values().stream().filter(s -> !s.busy.get() && !clock.instant().isBefore(s.expiresAt)).toList().forEach(this::close);
    }
    @PreDestroy public void shutdown() { sessions.values().stream().toList().forEach(this::close); }

    private static final class Session {
        final String id = UUID.randomUUID().toString();
        final String challengeId = UUID.randomUUID().toString();
        final String owner;
        final String documentType;
        final String document;
        final AtomicBoolean busy = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();
        final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "mtc-assisted-session"); thread.setDaemon(true); return thread;
        });
        volatile Instant expiresAt = Instant.now().plusSeconds(180);
        volatile int optionCount;
        MtcAssistedBrowser browser;
        Session(String owner, String documentType, String document) { this.owner = owner; this.documentType = documentType; this.document = document; }
    }
}

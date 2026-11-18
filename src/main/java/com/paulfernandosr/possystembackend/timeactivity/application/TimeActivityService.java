package com.paulfernandosr.possystembackend.timeactivity.application;

import com.paulfernandosr.possystembackend.timeactivity.domain.ActivityIntervals;
import com.paulfernandosr.possystembackend.timeactivity.domain.TimeActivity.*;
import com.paulfernandosr.possystembackend.timeactivity.domain.port.TimeActivityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class TimeActivityService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Set<String> INTERNAL = Set.of("ADMINISTRATOR", "CASHIER", "WHOLESALER", "WAREHOUSE");
    private static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("/time-activity", "VIEW_TIME_SELF"),
            Map.entry("/security/control", "VIEW_SECURITY_CONTROL"), Map.entry("/security/access", "VIEW_SECURITY_AUDIT"),
            Map.entry("/dashboard", "MANAGE_DASHBOARD"), Map.entry("/cash-registers", "MANAGE_CASH_REGISTER"),
            Map.entry("/user-cash", "MANAGE_USER_CASH"), Map.entry("/products", "MANAGE_PRODUCTS"),
            Map.entry("/product-offers", "MANAGE_PRODUCT_OFFERS"), Map.entry("/community", "MANAGE_FORUMS_VIEW"),
            Map.entry("/company-community", "MANAGE_COMPANY_COMMUNITY_VIEW"), Map.entry("/promotions", "MANAGE_PROMOTIONS"),
            Map.entry("/whatsapp-center", "MANAGE_WHATSAPP_CENTER"), Map.entry("/users", "MANAGE_USERS"),
            Map.entry("/customers/mine", "MANAGE_MY_CUSTOMERS"), Map.entry("/customers/map", "MANAGE_CUSTOMERS_MAP_VIEW"),
            Map.entry("/customers", "MANAGE_CUSTOMERS"), Map.entry("/sales", "MANAGE_SALES"),
            Map.entry("/sales/list", "MANAGE_SALES_LIST"), Map.entry("/purchases", "MANAGE_PURSCHASES"),
            Map.entry("/proformas", "MANAGE_PROFORMAS"), Map.entry("/proformas/list", "MANAGE_PROFORMAS_LIST"),
            Map.entry("/contracts", "MANAGE_CONTRACTS"), Map.entry("/contracts/new", "MANAGE_CONTRACTS"),
            Map.entry("/contracts/list", "MANAGE_CONTRACTS_LIST"),
            Map.entry("/motorcycle-availability", "MANAGE_MOTORCYCLE_AVAILABILITY_VIEW"),
            Map.entry("/accounts-receivable", "MANAGE_ACCOUNTS_RECEIVABLE"),
            Map.entry("/guide-remissions", "MANAGE_GUIDE_REMISSIONS"),
            Map.entry("/guide-remissions/list", "MANAGE_GUIDE_REMISSIONS_LIST"),
            Map.entry("/motorcycle-catalog", "MANAGE_MOTORCYCLE_CATALOG"),
            Map.entry("/motorcycle-catalog/list", "MANAGE_MOTORCYCLE_CATALOG_LIST"),
            Map.entry("/counter-sales", "MANAGE_COUNTER_SALES"), Map.entry("/counter-sales/list", "MANAGE_COUNTER_SALES_LIST"),
            Map.entry("/kardex", "MANAGE_KARDEX"));

    private final TimeActivityRepository store;
    private final int idleMinutes;
    private final int policyVersion;
    private final boolean readCommercialResults;

    public TimeActivityService(TimeActivityRepository store, int idleMinutes, int policyVersion, boolean readCommercialResults) {
        this.store = store;
        this.idleMinutes = Math.max(1, Math.min(60, idleMinutes));
        this.policyVersion = Math.max(1, policyVersion);
        this.readCommercialResults = readCommercialResults;
    }

    public record Identity(String username, String session, Set<String> permissions) {}
    public record Query(LocalDate date, String scope, Long userId, String module, String role) {}
    private record Window(List<Long> subjects, long from, long until, String module, String role) {}
    private record Coverage(boolean sales, boolean proformas) {}
    private record Group(long userId, String username, String role, String module) {}

    @Transactional(readOnly = true, timeout = 3)
    public Context context(Identity identity) {
        Actor actor = actor(identity);
        boolean enrolled = actor.enrolledSince() != null;
        return new Context(enrolled, enrolled ? contextId(identity) : null, Instant.now(), idleMinutes, policyVersion);
    }

    @Transactional(timeout = 4)
    public BatchReceipt ingest(Identity identity, Batch batch) {
        Actor actor = actor(identity);
        if(batch==null) invalidBatch();
        if (actor.enrolledSince() == null || !contextId(identity).equals(batch.contextId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!Objects.equals(batch.policyVersion(), policyVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La política de medición cambió.");
        }
        var observations = batch.observations();
        if (observations == null || observations.isEmpty() || observations.size() > 60) invalidBatch();
        long now = System.currentTimeMillis();
        long earliest = Math.max(now - 3_600_000, actor.enrolledSince().toEpochMilli());
        int accepted = 0;
        for (Observation o : observations) {
            if (o == null || o.eventId() == null || o.module() == null || !MODULES.containsKey(o.module())
                    || !identity.permissions().contains(MODULES.get(o.module())) || o.start() == null || o.end() == null
                    || o.start() < earliest || o.end() > now + 5_000 || o.end() <= o.start() || o.end() - o.start() > 45_000
                    || (o.lastInteraction() != null && (o.lastInteraction() < actor.enrolledSince().toEpochMilli()
                    || o.lastInteraction() > o.start()))) invalidBatch();
            try {
                if (store.save(actor, o, idleMinutes, policyVersion)) accepted++;
            } catch (IllegalArgumentException conflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La observación ya fue recibida con otro contenido.");
            }
        }
        return new BatchReceipt(accepted, observations.size() - accepted);
    }

    @Transactional(readOnly = true, timeout = 5)
    public Summary summary(Identity identity, Query query) {
        Window w = window(identity, query);
        var groups = new LinkedHashMap<Group, long[]>();
        for (var slices : slices(w).values()) {
            for (Slice slice : slices) {
                Segment s = slice.source();
                if (w.module() != null && !w.module().equals(s.module())) continue;
                if (w.role() != null && !w.role().equals(s.role())) continue;
                var key = new Group(s.userId(), s.username(), s.role(), s.module());
                long[] total = groups.computeIfAbsent(key, ignored -> new long[3]);
                Interval i = slice.interval();
                int slot = switch (i.state()) {
                    case RECENT -> 0;
                    case QUIET -> 1;
                    case UNCLASSIFIED -> 2;
                    case GAP -> throw new IllegalStateException("Gaps are excluded from summaries");
                };
                total[slot] += i.end() - i.start();
            }
        }
        var rows = groups.entrySet().stream().map(entry -> {
            Group k = entry.getKey();
            long[] v = entry.getValue();
            return new Row(k.userId(), k.username(), k.role(), k.module(), v[0], v[1], v[2]);
        }).toList();
        Coverage c = coverage(identity, w);
        Long confirmed = w.subjects().isEmpty() ? null
                : store.resultCount(w.subjects(), w.from(), w.until(), c.sales(), c.proformas());
        return new Summary(rows, confirmed);
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<Interval> timeline(Identity identity, Query query) {
        Window w = window(identity, query);
        if (w.subjects().size() != 1 || w.role() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return ActivityIntervals.timeline(slices(w).getOrDefault(w.subjects().getFirst(), List.of()), w.module());
    }

    @Transactional(readOnly = true, timeout = 5)
    public Operations operations(Identity identity, Query query) {
        Window w = window(identity, query);
        Coverage c = coverage(identity, w);
        var covered = new ArrayList<String>();
        if (c.sales()) covered.add("Ventas con confirmación idempotente");
        if (c.proformas()) covered.add("Alta de proformas");
        var results = w.subjects().isEmpty() ? List.<BusinessResult>of()
                : store.results(w.subjects(), w.from(), w.until(), c.sales(), c.proformas());
        return new Operations(results.stream().limit(100).toList(), covered, results.size() > 100);
    }

    private Coverage coverage(Identity identity, Window w) {
        if (!readCommercialResults || w.role() != null || w.subjects().isEmpty()) return new Coverage(false, false);
        boolean sales = (w.module() == null || Set.of("/sales", "/sales/list").contains(w.module()))
                && identity.permissions().contains("MANAGE_SALES_LIST") && store.hasSaleJournal();
        boolean proformas = (w.module() == null || Set.of("/proformas", "/proformas/list").contains(w.module()))
                && identity.permissions().contains("MANAGE_PROFORMAS_LIST") && store.hasProformas();
        return new Coverage(sales, proformas);
    }

    private Map<Long, List<Slice>> slices(Window w) {
        if (w.subjects().isEmpty()) return Map.of();
        var all = store.observations(w.subjects(), w.from(), w.until());
        if (all.size() > TimeActivityRepository.MAX_SEGMENTS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Demasiada actividad para una consulta.");
        }
        var byUser = new LinkedHashMap<Long, List<Segment>>();
        for (Segment s : all) byUser.computeIfAbsent(s.userId(), ignored -> new ArrayList<>()).add(s);
        var result = new LinkedHashMap<Long, List<Slice>>();
        byUser.forEach((id, segments) -> result.put(id, ActivityIntervals.calculate(segments, w.from(), w.until())));
        return result;
    }

    private Window window(Identity identity, Query query) {
        Actor actor = actor(identity);
        if (!identity.permissions().contains("VIEW_TIME_SELF")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String scope = query.scope();
        if (scope==null || !Set.of("self", "team").contains(scope)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String module = query.module();
        String role = query.role();
        if ((module != null && !MODULES.containsKey(module)) || (role != null && !INTERNAL.contains(role))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        LocalDate today = LocalDate.now(LIMA);
        if (query.date()==null || query.date().isAfter(today) || query.date().isBefore(today.minusDays(30))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Consulta un día de los últimos 31 días.");
        }
        List<Long> subjects = List.of(actor.userId());
        Long chosen = query.userId();
        if (scope.equals("team") || (chosen != null && chosen != actor.userId())) {
            if (!identity.permissions().contains("VIEW_TIME_TEAM")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            subjects = store.team(actor.userId());
            if (subjects.size() > 50) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El alcance excede el límite del piloto.");
        }
        if (chosen != null) {
            if (!subjects.contains(chosen)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            subjects = List.of(chosen);
        }
        return new Window(subjects, query.date().atStartOfDay(LIMA).toInstant().toEpochMilli(),
                query.date().plusDays(1).atStartOfDay(LIMA).toInstant().toEpochMilli(), module, role);
    }

    private Actor actor(Identity identity) {
        return store.actor(identity.username()).filter(a -> INTERNAL.contains(a.role()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    private static String contextId(Identity identity) {
        try {
            byte[] input = ("imbasac/time-activity/v1:" + identity.session()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void invalidBatch() {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lote de observaciones no válido.");
    }

}

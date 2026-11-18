package com.paulfernandosr.possystembackend.timeactivity.infrastructure;

import com.paulfernandosr.possystembackend.timeactivity.domain.TimeActivity.*;
import com.paulfernandosr.possystembackend.timeactivity.domain.port.TimeActivityRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TimeActivityStore implements TimeActivityRepository {
    private final JdbcClient jdbc;

    public TimeActivityStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Actor> actor(String username) {
        return jdbc.sql("""
                SELECT u.id, u.username, r.name::text role, e.enabled_since
                FROM users u JOIN roles r ON r.id = u.role_id
                LEFT JOIN time_activity_enrollment e ON e.user_id = u.id AND e.enabled AND e.enabled_since<=CURRENT_TIMESTAMP
                WHERE u.username = :username AND u.enabled
                """).param("username", username).query((rs, n) -> {
                    Timestamp enrolled = rs.getTimestamp("enabled_since");
                    return new Actor(rs.getLong("id"), rs.getString("username"), rs.getString("role"),
                            enrolled == null ? null : enrolled.toInstant());
                }).optional();
    }

    public List<Long> team(long viewer) {
        return jdbc.sql("""
                SELECT s.member_user_id FROM time_activity_team_scope s
                JOIN users u ON u.id = s.member_user_id
                JOIN roles r ON r.id = u.role_id
                WHERE s.viewer_user_id = :viewer AND r.name::text IN ('ADMINISTRATOR','CASHIER','WHOLESALER','WAREHOUSE')
                ORDER BY s.member_user_id LIMIT 51
                """).param("viewer", viewer).query(Long.class).list();
    }

    public boolean save(Actor actor, Observation o, int idleMinutes, int policy) {
        int inserted = jdbc.sql("""
                INSERT INTO time_activity_observation
                    (event_id, user_id, username, role, module, start_ms, end_ms, interaction_ms, idle_minutes, policy_version)
                VALUES (:id,:userId,:username,:role,:module,:start,:end,:interaction,:idle,:policy)
                ON CONFLICT (event_id) DO NOTHING
                """).param("id", o.eventId()).param("userId", actor.userId()).param("username", actor.username())
                .param("role", actor.role()).param("module", o.module()).param("start", o.start()).param("end", o.end())
                .param("interaction", o.lastInteraction(), Types.BIGINT).param("idle", idleMinutes).param("policy", policy).update();
        if (inserted == 1) return true;
        boolean same = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM time_activity_observation WHERE event_id = :id
                AND user_id = :userId AND module = :module AND start_ms = :start AND end_ms = :end
                AND interaction_ms IS NOT DISTINCT FROM CAST(:interaction AS bigint) AND policy_version = :policy)
                """).param("id", o.eventId()).param("userId", actor.userId()).param("module", o.module())
                .param("start", o.start()).param("end", o.end()).param("interaction", o.lastInteraction(), Types.BIGINT)
                .param("policy", policy).query(Boolean.class).single();
        if (!same) throw new IllegalArgumentException("Conflicting observation ID");
        return false;
    }

    public List<Segment> observations(List<Long> users, long from, long until) {
        return jdbc.sql("""
                SELECT event_id, user_id, username, role, module, start_ms, end_ms, interaction_ms, idle_minutes
                FROM time_activity_observation
                WHERE user_id IN (:users) AND start_ms >= :lower AND start_ms < :until AND end_ms > :from
                ORDER BY user_id, start_ms, event_id LIMIT :limit
                """).param("users", users).param("lower", from - 45_000).param("from", from).param("until", until)
                .param("limit", MAX_SEGMENTS + 1).query((rs, n) -> new Segment(
                        rs.getObject("event_id", java.util.UUID.class), rs.getLong("user_id"), rs.getString("username"),
                        rs.getString("role"), rs.getString("module"), rs.getLong("start_ms"), rs.getLong("end_ms"),
                        rs.getObject("interaction_ms", Long.class), rs.getLong("idle_minutes") * 60_000)).list();
    }

    public boolean hasSaleJournal() {
        return commercialTimestamp("sale_create_idempotency","completed_at",Set.of("user_id","sale_id","response_json","completed_at"),"j").isPresent();
    }
    public boolean hasProformas() {
        return commercialTimestamp("proforma","created_at",Set.of("created_by","series","number","created_at"),"p").isPresent();
    }

    /** Schema names and SQL expressions are fixed internally, never selected by a request. */
    private Optional<String> commercialTimestamp(String table,String column,Set<String> required,String alias) {
        Map<String,Integer> columns=jdbc.sql("SELECT attname,atttypid::integer FROM pg_attribute WHERE attrelid=to_regclass(:table) AND attnum>0 AND NOT attisdropped")
                .param("table",table).query((rs,n)->Map.entry(rs.getString("attname"),rs.getInt("atttypid")))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        if(!columns.keySet().containsAll(required)) return Optional.empty();
        // Existing local timestamps describe Lima; timestamptz already represents an instant.
        return switch(columns.get(column)) {
            case 1114 -> Optional.of("("+alias+"."+column+" AT TIME ZONE 'America/Lima')");
            case 1184 -> Optional.of(alias+"."+column);
            default -> Optional.empty();
        };
    }

    private static final String SALE_QUERY = """
            SELECT DISTINCT ON (j.sale_id) 'SALE' kind, j.completed_at happened_at, u.username actor,
              concat_ws('-', j.response_json->>'series', j.response_json->>'number') reference
            FROM sale_create_idempotency j JOIN users u ON u.id = j.user_id
            JOIN time_activity_enrollment e ON e.user_id = j.user_id AND j.completed_at >= e.enabled_since
              AND (e.enabled OR j.completed_at < e.disabled_at)
            WHERE j.user_id IN (:users) AND j.completed_at >= :from AND j.completed_at < :until
              AND j.sale_id IS NOT NULL AND j.response_json IS NOT NULL
            ORDER BY j.sale_id, j.completed_at
            """;
    private static final String PROFORMA_QUERY = """
            SELECT 'PROFORMA' kind, p.created_at happened_at, u.username actor,
              concat_ws('-', p.series, p.number) reference
            FROM proforma p JOIN users u ON u.id = p.created_by
            JOIN time_activity_enrollment e ON e.user_id = p.created_by AND p.created_at >= e.enabled_since
              AND (e.enabled OR p.created_at < e.disabled_at)
            WHERE p.created_by IN (:users) AND p.created_at >= :from AND p.created_at < :until
            """;

    public List<BusinessResult> results(List<Long> users, long from, long until, boolean sales, boolean proformas) {
        if (!sales && !proformas) return List.of();
        String source = sources(sales, proformas);
        return jdbc.sql("SELECT * FROM (" + source + ") results ORDER BY happened_at DESC, kind, reference LIMIT 101")
                .param("users", users).param("from", Timestamp.from(Instant.ofEpochMilli(from)))
                .param("until", Timestamp.from(Instant.ofEpochMilli(until)))
                .query((rs, n) -> new BusinessResult(rs.getString("kind"), rs.getString("reference"),
                        rs.getString("actor"), rs.getTimestamp("happened_at").toInstant().toEpochMilli())).list();
    }

    public Long resultCount(List<Long> users, long from, long until, boolean sales, boolean proformas) {
        if (!sales && !proformas) return null;
        return jdbc.sql("SELECT count(*) FROM (" + sources(sales, proformas) + ") results")
                .param("users", users).param("from", Timestamp.from(Instant.ofEpochMilli(from)))
                .param("until", Timestamp.from(Instant.ofEpochMilli(until))).query(Long.class).single();
    }

    private String sources(boolean sales, boolean proformas) {
        String sale=sales?SALE_QUERY.replace("j.completed_at",commercialTimestamp("sale_create_idempotency","completed_at",Set.of("user_id","sale_id","response_json","completed_at"),"j").orElseThrow(()->new IllegalStateException("Commercial sale schema unavailable"))):null;
        String proforma=proformas?PROFORMA_QUERY.replace("p.created_at",commercialTimestamp("proforma","created_at",Set.of("created_by","series","number","created_at"),"p").orElseThrow(()->new IllegalStateException("Commercial proforma schema unavailable"))):null;
        if (sales && proformas) return "(" + sale + ") UNION ALL (" + proforma + ")";
        return sales ? sale : proforma;
    }
}

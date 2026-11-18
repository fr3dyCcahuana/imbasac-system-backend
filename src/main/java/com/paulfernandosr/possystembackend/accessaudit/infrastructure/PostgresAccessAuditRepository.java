package com.paulfernandosr.possystembackend.accessaudit.infrastructure;

import com.paulfernandosr.possystembackend.accessaudit.domain.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.port.AccessAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PostgresAccessAuditRepository implements AccessAuditRepository {
    private final JdbcClient jdbc;
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    private volatile long schemaCheckedAt;
    private volatile boolean contextColumnReady;
    private static final String EVENT_SELECT="SELECT security_access_event.*, "+AuditCategory.SQL+" AS category, to_jsonb(security_access_event)->'authentication_context' AS auth_context FROM security_access_event";

    @Override public synchronized boolean authenticationContextReady() {
        long now=System.currentTimeMillis();
        if(now-schemaCheckedAt>=60000) {
            contextColumnReady=jdbc.sql("SELECT EXISTS(SELECT 1 FROM pg_attribute WHERE attrelid=to_regclass('security_access_event') AND attname='authentication_context' AND attnum>0 AND NOT attisdropped)")
                    .query(Boolean.class).single();
            schemaCheckedAt=now;
        }
        return contextColumnReady;
    }
    @Override public Optional<String> sessionRejectionReason(String key) {
        return jdbc.sql("""
                SELECT CASE WHEN expires_at<=CURRENT_TIMESTAMP THEN 'SESSION_EXPIRED'
                    WHEN state='REPLACED' THEN 'SESSION_REPLACED'
                    WHEN state='REVOKED' THEN 'SESSION_REVOKED'
                    WHEN state='LOGGED_OUT' THEN 'SESSION_LOGGED_OUT'
                    WHEN state='INVALIDATED' THEN 'SESSION_INVALIDATED'
                    ELSE 'SESSION_NOT_ACTIVE' END
                FROM security_access_session WHERE session_key=:key
                """).param("key",key).query(String.class).optional();
    }

    @Override public void lockUser(String username) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:u,29))").param("u",username).query((rs,n)->0).single();
    }
    @Override public void start(String key, String username, long ttl, Metadata metadata) {
        var previous = jdbc.sql("SELECT session_key FROM security_access_session WHERE user_id=(SELECT id FROM users WHERE username=:u) AND state='ACTIVE'")
                .param("u",username).query(String.class).list();
        for (String old : previous) end(old,"SESSION_REPLACED","NEW_LOGIN",metadata);
        int inserted = jdbc.sql("""
                INSERT INTO security_access_session(id,session_key,username,user_id,role_name,expires_at,state,peer_address,user_agent)
                SELECT :id,:key,u.username,u.id,r.name::text,CURRENT_TIMESTAMP + (:ttl * INTERVAL '1 second'),'ACTIVE',:peer,:agent
                FROM users u JOIN roles r ON r.id=u.role_id WHERE u.username=:u AND u.enabled=TRUE
                """).param("id",UUID.randomUUID()).param("key",key).param("u",username).param("ttl",ttl)
                .param("peer",metadata.peerAddress()).param("agent",metadata.userAgent()).update();
        if (inserted != 1) throw AuditException.unavailable();
        record("LOGIN_GRANTED","GRANTED","BACKEND",username,null,key,null,"CREDENTIALS_AND_SESSION_ACCEPTED",metadata,false);
    }
    @Override public boolean active(String key, String username) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM security_access_session WHERE session_key=:k AND username=:u AND state='ACTIVE' AND expires_at>CURRENT_TIMESTAMP)")
                .param("k",key).param("u",username).query(Boolean.class).single();
    }
    @Override public void touch(String key) {
        jdbc.sql("UPDATE security_access_session SET last_seen_at=CURRENT_TIMESTAMP WHERE session_key=:k AND state='ACTIVE' AND last_seen_at<CURRENT_TIMESTAMP-INTERVAL '1 minute'")
                .param("k",key).update();
    }
    @Override public boolean extend(String key, long ttl) {
        return jdbc.sql("UPDATE security_access_session SET expires_at=CURRENT_TIMESTAMP+(:ttl*INTERVAL '1 second'),last_seen_at=CURRENT_TIMESTAMP WHERE session_key=:k AND state='ACTIVE' AND expires_at>CURRENT_TIMESTAMP")
                .param("k",key).param("ttl",ttl).update()==1;
    }
    @Override public void end(String key, String type, String reason, Metadata metadata) {
        var rows = jdbc.sql("SELECT username,expires_at<=CURRENT_TIMESTAMP AS expired FROM security_access_session WHERE session_key=:k AND state='ACTIVE' FOR UPDATE")
                .param("k",key).query((rs,n)->Map.entry(rs.getString("username"),rs.getBoolean("expired"))).list();
        if (rows.isEmpty()) return;
        boolean expired = rows.getFirst().getValue();
        String state = expired ? "EXPIRED" : switch(type) {
            case "LOGOUT" -> "LOGGED_OUT";
            case "SESSION_REPLACED" -> "REPLACED";
            default -> "INVALIDATED";
        };
        jdbc.sql("UPDATE security_access_session SET state=:s,ended_at=CASE WHEN :s='EXPIRED' THEN expires_at ELSE CURRENT_TIMESTAMP END,end_reason=:r WHERE session_key=:k")
                .param("s",state).param("r",expired ? "EXPIRY_OBSERVED" : reason).param("k",key).update();
        record(expired ? "SESSION_EXPIRED" : type,"INFO","BACKEND",rows.getFirst().getKey(),null,key,null,
                expired ? "EXPIRY_OBSERVED" : reason,metadata,false);
    }
    @Override public void endUser(String username, Metadata metadata) {
        var keys = jdbc.sql("SELECT session_key FROM security_access_session WHERE (username=:u OR user_id=(SELECT id FROM users WHERE username=:u)) AND state='ACTIVE'")
                .param("u",username).query(String.class).list();
        for (String key : keys) end(key,"SESSION_INVALIDATED","USER_SESSIONS_INVALIDATED",metadata);
    }
    @Override public void record(String type, String outcome, String source, String username, String attempted,
                                 String key, String actor, String reason, Metadata metadata, boolean aggregate) {
        boolean capture=authenticationContextReady();
        String context=capture?contextJson(metadata.authentication()):null;
        String bucket = aggregate ? digest(type+"|"+metadata.peerAddress()+"|"+username+"|"+attempted+"|"+reason+"|"+metadata.userAgent()+"|"+metadata.authentication()+"|"+Instant.now().getEpochSecond()/60) : null;
        String insert="""
                INSERT INTO security_access_event(id,event_type,outcome,source,username,attempted_username,role_name,
                  session_id,actor_username,reason,peer_address,user_agent,correlation_id,aggregation_key,user_id,actor_user_id%s)
                VALUES(:id,:type,:outcome,:source,:u,:attempt,
                  COALESCE((SELECT role_name FROM security_access_session WHERE session_key=:key),
                    (SELECT r.name::text FROM users u JOIN roles r ON r.id=u.role_id WHERE u.username=:u)),
                  (SELECT id FROM security_access_session WHERE session_key=:key),:actor,:reason,:peer,:agent,:correlation,:bucket,
                  COALESCE((SELECT user_id FROM security_access_session WHERE session_key=:key),(SELECT id FROM users WHERE username=:u)),
                  (SELECT id FROM users WHERE username=:actor)%s)
                ON CONFLICT(aggregation_key) DO UPDATE SET occurrences=security_access_event.occurrences+1,
                  last_occurred_at=GREATEST(security_access_event.last_occurred_at,EXCLUDED.last_occurred_at)
                """.formatted(capture?",authentication_context":"",capture?",CAST(:context AS JSONB)":"");
        var statement=jdbc.sql(insert).param("id",UUID.randomUUID()).param("type",type).param("outcome",outcome).param("source",source)
                .param("u",AuditRequestMetadata.clean(username,160)).param("attempt",AuditRequestMetadata.clean(attempted,160))
                .param("key",key).param("actor",AuditRequestMetadata.clean(actor,160)).param("reason",AuditRequestMetadata.clean(reason,300))
                .param("peer",metadata.peerAddress()).param("agent",metadata.userAgent()).param("correlation",metadata.correlationId())
                .param("bucket",bucket);
        if(capture) statement=statement.param("context",context);
        statement.update();
        if(metadata.location()!=null) {
            var location=metadata.location();
            jdbc.sql("""
                    INSERT INTO security_access_location(correlation_id,status,country,region,city,accuracy_radius_km,source,database_version,observed_at)
                    VALUES(:id,:status,:country,:region,:city,:radius,:source,:version,:observed)
                    ON CONFLICT(correlation_id) DO NOTHING
                    """).param("id",metadata.correlationId()).param("status",location.status()).param("country",location.country())
                    .param("region",AuditRequestMetadata.clean(location.region(),160)).param("city",AuditRequestMetadata.clean(location.city(),160))
                    .param("radius",location.accuracyRadiusKm()).param("source",location.source()).param("version",location.databaseVersion())
                    .param("observed",java.sql.Timestamp.from(location.observedAt())).update();
        }
    }
    @Override public Page<Event> events(AuditQuery query, int limit, int offset) {
        Filter filter = eventsFilter(query);
        long total = jdbc.sql("SELECT COUNT(*) FROM security_access_event WHERE "+filter.sql()).params(filter.params()).query(Long.class).single();
        List<Event> items = jdbc.sql(EVENT_SELECT+" WHERE "+filter.sql()+" ORDER BY occurred_at DESC,id DESC LIMIT :limit OFFSET :offset")
                .params(filter.params()).param("limit",limit).param("offset",offset).query(EVENT).list();
        return new Page<>(items,total,query.page(),query.size());
    }
    @Override public Optional<Event> event(UUID id) {
        return jdbc.sql(EVENT_SELECT+" WHERE id=:id").param("id",id).query(EVENT).optional();
    }
    @Override public Page<SessionRow> sessions(AuditQuery query, String state) {
        String calculated = "CASE WHEN state='ACTIVE' AND expires_at<=CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE state END";
        Filter f = sessionFilter(query,true);
        String where = f.sql() + (state == null ? "" : " AND ("+calculated+")=:state");
        Map<String,Object> params = new HashMap<>(f.params()); if (state != null) params.put("state",state);
        long total = jdbc.sql("SELECT COUNT(*) FROM security_access_session WHERE "+where).params(params).query(Long.class).single();
        List<SessionRow> items = jdbc.sql("SELECT id,username,user_id,role_name,started_at,last_seen_at,expires_at,"+calculated+" AS state,ended_at,end_reason,peer_address,user_agent FROM security_access_session WHERE "+where+" ORDER BY started_at DESC,id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit",query.size()).param("offset",query.page()*query.size()).query(SESSION).list();
        return new Page<>(items,total,query.page(),query.size());
    }
    @Override public Summary summary(AuditQuery query) {
        Filter f = eventsFilter(query);
        String from = " FROM security_access_event WHERE "+f.sql();
        long[] totals = jdbc.sql("""
                SELECT COALESCE(SUM(occurrences) FILTER(WHERE event_type='LOGIN_GRANTED'),0) AS granted,
                  COALESCE(SUM(occurrences) FILTER(WHERE event_type='LOGIN_REJECTED'),0) AS rejected,
                  COUNT(DISTINCT user_id) FILTER(WHERE event_type='LOGIN_GRANTED') AS users,
                  COALESCE(SUM(occurrences) FILTER(WHERE event_type='LOGIN_GRANTED' AND
                    (EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'America/Lima')<7 OR EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'America/Lima')>=21)),0) AS outside
                """+from).params(f.params()).query((rs,n)->new long[]{rs.getLong("granted"),rs.getLong("rejected"),rs.getLong("users"),rs.getLong("outside")}).single();
        List<Hour> hours = jdbc.sql("SELECT EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'America/Lima')::integer AS hour,COALESCE(SUM(occurrences) FILTER(WHERE event_type='LOGIN_GRANTED'),0) AS granted,COALESCE(SUM(occurrences) FILTER(WHERE event_type='LOGIN_REJECTED'),0) AS rejected"+from+" GROUP BY 1 ORDER BY 1")
                .params(f.params()).query((rs,n)->new Hour(rs.getInt("hour"),rs.getLong("granted"),rs.getLong("rejected"))).list();
        List<Daily> days = jdbc.sql("SELECT (occurred_at AT TIME ZONE 'America/Lima')::date::text AS day,COALESCE(SUM(occurrences) FILTER(WHERE event_type='LOGIN_GRANTED'),0) AS granted,COALESCE(SUM(occurrences) FILTER(WHERE event_type='LOGIN_REJECTED'),0) AS rejected,COUNT(DISTINCT user_id) FILTER(WHERE event_type='LOGIN_GRANTED') AS users"+from+" GROUP BY 1 ORDER BY 1")
                .params(f.params()).query((rs,n)->new Daily(rs.getString("day"),rs.getLong("granted"),rs.getLong("rejected"),rs.getLong("users"))).list();
        Filter sf = sessionFilter(query,false);
        long active = jdbc.sql("SELECT COUNT(*) FROM security_access_session WHERE "+sf.sql()+" AND state='ACTIVE' AND expires_at>CURRENT_TIMESTAMP")
                .params(sf.params()).query(Long.class).single();
        return new Summary(totals[0],totals[1],totals[2],active,totals[3],hours,days);
    }
    @Override public boolean revoke(UUID id, String actor, String reason, Metadata metadata) {
        var keys = jdbc.sql("SELECT session_key,username FROM security_access_session WHERE id=:id AND state='ACTIVE' AND expires_at>CURRENT_TIMESTAMP FOR UPDATE")
                .param("id",id).query((rs,n)->Map.entry(rs.getString("session_key"),rs.getString("username"))).list();
        if (keys.isEmpty()) return false;
        jdbc.sql("UPDATE security_access_session SET state='REVOKED',ended_at=CURRENT_TIMESTAMP,end_reason=:reason WHERE id=:id")
                .param("reason",reason).param("id",id).update();
        record("SESSION_REVOKED","INFO","BACKEND",keys.getFirst().getValue(),null,keys.getFirst().getKey(),actor,reason,metadata,false);
        return true;
    }
    @Override public Instant firstEvent() {
        return jdbc.sql("SELECT MIN(occurred_at) AS first FROM security_access_event")
                .query((rs,n)->instant(rs,"first")).list().getFirst();
    }
    @Override public Optional<SessionReference> sessionReference(UUID id) {
        return jdbc.sql("SELECT session_key,username FROM security_access_session WHERE id=:id").param("id",id)
                .query((rs,n)->new SessionReference(rs.getString("session_key"),rs.getString("username"))).optional();
    }

    private record Filter(String sql, Map<String,Object> params) {}
    private Filter eventsFilter(AuditQuery q) {
        StringBuilder sql = new StringBuilder("occurred_at>=:from AND occurred_at<:until");
        Map<String,Object> p = new HashMap<>(); p.put("from",java.sql.Timestamp.from(q.from())); p.put("until",java.sql.Timestamp.from(q.until()));
        if (q.username()!=null) { sql.append(" AND (LOWER(username)=LOWER(:username) OR LOWER(attempted_username)=LOWER(:username))"); p.put("username",q.username()); }
        if (q.role()!=null) { sql.append(" AND role_name=:role"); p.put("role",q.role()); }
        if (q.type()!=null) { sql.append(" AND event_type=:type"); p.put("type",q.type()); }
        if (q.outcome()!=null) { sql.append(" AND outcome=:outcome"); p.put("outcome",q.outcome()); }
        if (q.category()!=null) { sql.append(" AND (").append(AuditCategory.SQL).append(")=:category"); p.put("category",q.category()); }
        return new Filter(sql.toString(),p);
    }
    private Filter sessionFilter(AuditQuery q, boolean dates) {
        StringBuilder sql = new StringBuilder("TRUE"); Map<String,Object> p = new HashMap<>();
        if (dates) {
            sql.append(" AND ((started_at>=:from AND started_at<:until) OR (state='ACTIVE' AND expires_at>CURRENT_TIMESTAMP))");
            p.put("from",java.sql.Timestamp.from(q.from())); p.put("until",java.sql.Timestamp.from(q.until()));
        }
        if (q.username()!=null) { sql.append(" AND LOWER(username)=LOWER(:username)"); p.put("username",q.username()); }
        if (q.role()!=null) { sql.append(" AND role_name=:role"); p.put("role",q.role()); }
        return new Filter(sql.toString(),p);
    }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        var value = rs.getTimestamp(name); return value==null ? null : value.toInstant();
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String contextJson(AuthenticationContext context) {
        if(context==null) return null;
        if(context.fingerprintSha256()!=null) {
            final var observed=context;
            var devices=jdbc.sql("SELECT d.id,d.display_name FROM security_device_certificate c JOIN security_device d ON d.id=c.device_id WHERE c.fingerprint_sha256=:fp")
                    .param("fp",observed.fingerprintSha256()).query((rs,n)->new AuthenticationContext(observed.certificateState(),observed.fingerprintSha256(),observed.serialHex(),rs.getObject("id",UUID.class),rs.getString("display_name"),observed.failureReason())).list();
            if(!devices.isEmpty()) context=devices.getFirst();
        }
        try { return JSON.writeValueAsString(context); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Audit context could not be encoded"); }
    }
    private static AuthenticationContext readContext(ResultSet rs) throws SQLException {
        String raw=rs.getString("auth_context");
        if(raw==null) return null;
        try { return JSON.readValue(raw,AuthenticationContext.class); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { return null; }
    }
    private static final RowMapper<Event> EVENT = (rs,n)->new Event(rs.getObject("id",UUID.class),instant(rs,"occurred_at"),instant(rs,"last_occurred_at"),rs.getString("event_type"),rs.getString("outcome"),rs.getString("source"),rs.getString("username"),rs.getString("attempted_username"),rs.getString("role_name"),rs.getObject("session_id",UUID.class),rs.getString("actor_username"),rs.getString("reason"),rs.getString("peer_address"),rs.getString("user_agent"),rs.getObject("correlation_id",UUID.class),rs.getLong("occurrences"),rs.getObject("user_id",Long.class),rs.getObject("actor_user_id",Long.class),rs.getString("category"),readContext(rs));
    private static final RowMapper<SessionRow> SESSION = (rs,n)->new SessionRow(rs.getObject("id",UUID.class),rs.getString("username"),rs.getString("role_name"),instant(rs,"started_at"),instant(rs,"last_seen_at"),instant(rs,"expires_at"),rs.getString("state"),instant(rs,"ended_at"),rs.getString("end_reason"),rs.getString("peer_address"),rs.getString("user_agent"),rs.getObject("user_id",Long.class));
}

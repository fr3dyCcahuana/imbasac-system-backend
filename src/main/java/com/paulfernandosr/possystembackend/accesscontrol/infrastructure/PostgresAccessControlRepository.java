package com.paulfernandosr.possystembackend.accesscontrol.infrastructure;

import com.paulfernandosr.possystembackend.accesscontrol.domain.*;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.*;

@Repository
public class PostgresAccessControlRepository implements AccessControlRepository {
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate strikeTx;
    private final Clock clock;
    @Autowired public PostgresAccessControlRepository(JdbcClient jdbc,PlatformTransactionManager manager) {
        this(jdbc,manager,Clock.systemUTC());
    }
    public PostgresAccessControlRepository(JdbcClient jdbc,PlatformTransactionManager manager,Clock clock) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.clock=clock;
        this.strikeTx=new TransactionTemplate(manager);
        this.strikeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public int blockDays() { return jdbc.sql("SELECT block_days FROM security_access_control_settings WHERE singleton=TRUE").query(Integer.class).single(); }
    @Override public Decision checkAccount(long userId) {
        Instant observedAt=clock.instant();
        var current=jdbc.sql("SELECT cycle_id,strike_count,blocked_until,strike_day FROM security_user_access_control WHERE user_id=:u")
                .param("u",userId).query((rs,n)->readState(rs,observedAt)).optional();
        if(current.isEmpty()) return new Decision(false,0,null);
        State state=current.get();
        if(state.blocked() || state.until()==null && state.sameDay()) return state.decision();
        return tx.execute(status->{lockUser(userId); return state(userId,true).decision();});
    }
    @Override public Decision recordUnauthorizedDevice(long userId,UUID deviceId,UUID attemptId,int threshold,String originKey,int cooldownSeconds) {
        return strikeTx.execute(status->{
            lockUser(userId); Instant now=clock.instant(); State state=state(userId,true,now);
            int fresh=jdbc.sql("INSERT INTO security_access_attempt(user_id,attempt_id,first_seen_at) VALUES(:u,:a,:now) ON CONFLICT(user_id,attempt_id) DO NOTHING")
                    .param("u",userId).param("a",attemptId).param("now",Timestamp.from(now)).update();
            if(fresh==0 || state.blocked()) return state.decision();
            boolean sameIncident=jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM security_access_strike WHERE user_id=:u AND cycle_id=:cycle
                    AND device_id IS NOT DISTINCT FROM CAST(:d AS UUID) AND origin_key IS NOT DISTINCT FROM CAST(:origin AS VARCHAR)
                    AND (occurred_at AT TIME ZONE 'America/Lima')::date=:day AND occurred_at>:cutoff)
                    """).param("u",userId).param("cycle",state.cycle()).param("d",deviceId).param("origin",originKey)
                    .param("day",java.sql.Date.valueOf(state.day()))
                    .param("cutoff",Timestamp.from(now.minusSeconds(cooldownSeconds))).query(Boolean.class).single();
            if(sameIncident) return state.decision();
            int next=state.strikes()+1;
            Instant until=next>=threshold?now.plus(Duration.ofDays(blockDays())):null;
            jdbc.sql("""
                INSERT INTO security_access_strike(id,user_id,cycle_id,attempt_id,occurred_at,device_id,origin_key,reason,threshold,blocked_until)
                VALUES(:id,:u,:cycle,:a,:now,:d,:origin,'UNAUTHORIZED_DEVICE',:threshold,:until)
                """).param("id",UUID.randomUUID()).param("u",userId).param("cycle",state.cycle()).param("a",attemptId)
                    .param("now",Timestamp.from(now)).param("d",deviceId).param("origin",originKey).param("threshold",threshold).param("until",timestamp(until)).update();
            jdbc.sql("UPDATE security_user_access_control SET strike_count=:n,blocked_until=:until,updated_at=:now WHERE user_id=:u")
                    .param("n",next).param("until",timestamp(until)).param("now",Timestamp.from(now)).param("u",userId).update();
            if(until!=null) change(userId,"ACCOUNT_BLOCKED",null,"STRIKE_THRESHOLD_REACHED","threshold="+threshold+";blockedUntil="+until);
            return new Decision(until!=null,next,until);
        });
    }
    @Override public boolean isDeviceAuthorized(long userId,UUID deviceId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM security_user_device_binding b JOIN security_device d ON d.id=b.device_id
                WHERE b.user_id=:u AND b.device_id=:d AND b.revoked_at IS NULL AND d.status='ACTIVE')
                """).param("u",userId).param("d",deviceId).query(Boolean.class).single();
    }
    private static final String USER_SELECT="""
            SELECT u.id,u.username,r.name::text role_name,u.enabled,COALESCE(q.device_quota,1) device_quota,
              (SELECT COUNT(*) FROM security_user_device_binding b WHERE b.user_id=u.id AND b.revoked_at IS NULL) active_devices,
              CASE WHEN c.blocked_until>:now THEN c.strike_count
                   WHEN c.blocked_until<=:now THEN 0
                   WHEN c.strike_day=(CAST(:now AS TIMESTAMPTZ) AT TIME ZONE 'America/Lima')::date THEN c.strike_count
                   ELSE 0 END strikes,
              c.cycle_id,c.blocked_until,COALESCE(c.blocked_until>:now,FALSE) blocked
            FROM users u JOIN roles r ON r.id=u.role_id LEFT JOIN security_user_device_quota q ON q.user_id=u.id
            LEFT JOIN security_user_access_control c ON c.user_id=u.id
            """;
    private static final RowMapper<UserControl> USER=(rs,n)->new UserControl(rs.getLong("id"),rs.getString("username"),rs.getString("role_name"),rs.getBoolean("enabled"),rs.getInt("device_quota"),rs.getLong("active_devices"),rs.getInt("strikes"),rs.getObject("cycle_id",UUID.class),instant(rs,"blocked_until"),rs.getBoolean("blocked"));
    @Override public Page<UserControl> users(String search,int page,int size) {
        String pattern=pattern(search);
        List<UserControl> rows=jdbc.sql(USER_SELECT+" WHERE u.username ILIKE :search ESCAPE '!' ORDER BY u.username,u.id LIMIT :size OFFSET :offset")
                .param("search",pattern).param("now",Timestamp.from(clock.instant())).param("size",size).param("offset",page*size).query(USER).list();
        long count=jdbc.sql("SELECT COUNT(*) FROM users WHERE username ILIKE :search ESCAPE '!'").param("search",pattern).query(Long.class).single();
        return new Page<>(rows,count,page,size);
    }
    @Override public UserControl user(long userId) {
        return jdbc.sql(USER_SELECT+" WHERE u.id=:u").param("u",userId).param("now",Timestamp.from(clock.instant())).query(USER).optional().orElseThrow(()->new AccessControlException(ACCESS_CONTROL_NOT_FOUND));
    }
    @Override public Page<DeviceRow> devices(String search,int page,int size) {
        var rows=jdbc.sql("SELECT id,display_name,platform,status,created_at FROM security_device WHERE display_name ILIKE :search ESCAPE '!' ORDER BY created_at DESC,id LIMIT :size OFFSET :offset")
                .param("search",pattern(search)).param("size",size).param("offset",page*size)
                .query((rs,n)->new DeviceRow(rs.getObject("id",UUID.class),rs.getString("display_name"),rs.getString("platform"),rs.getString("status"),instant(rs,"created_at"))).list();
        long count=jdbc.sql("SELECT COUNT(*) FROM security_device WHERE display_name ILIKE :search ESCAPE '!'").param("search",pattern(search)).query(Long.class).single();
        return new Page<>(rows,count,page,size);
    }
    @Override public List<Binding> bindings(long userId) {
        user(userId);
        return jdbc.sql("""
                SELECT b.id,b.user_id,b.device_id,d.display_name,d.platform,d.status,b.created_at,b.revoked_at
                FROM security_user_device_binding b JOIN security_device d ON d.id=b.device_id WHERE b.user_id=:u AND b.revoked_at IS NULL
                ORDER BY b.created_at DESC,b.id
                """).param("u",userId).query((rs,n)->new Binding(rs.getObject("id",UUID.class),rs.getLong("user_id"),rs.getObject("device_id",UUID.class),rs.getString("display_name"),rs.getString("platform"),rs.getString("status"),instant(rs,"created_at"),instant(rs,"revoked_at"))).list();
    }
    @Override public Page<Strike> strikes(long userId,int page,int size) {
        user(userId);
        var rows=jdbc.sql("SELECT * FROM security_access_strike WHERE user_id=:u ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset")
                .param("u",userId).param("size",size).param("offset",page*size)
                .query((rs,n)->new Strike(rs.getObject("id",UUID.class),rs.getLong("user_id"),rs.getObject("cycle_id",UUID.class),instant(rs,"occurred_at"),rs.getObject("device_id",UUID.class),rs.getString("reason"),rs.getInt("threshold"),instant(rs,"blocked_until"))).list();
        long count=jdbc.sql("SELECT COUNT(*) FROM security_access_strike WHERE user_id=:u").param("u",userId).query(Long.class).single();
        return new Page<>(rows,count,page,size);
    }
    @Override public Page<Change> history(long userId,int page,int size) {
        user(userId);
        var rows=jdbc.sql("SELECT * FROM security_access_control_change WHERE user_id=:u OR user_id IS NULL ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset")
                .param("u",userId).param("size",size).param("offset",page*size)
                .query((rs,n)->new Change(rs.getObject("id",UUID.class),rs.getLong("user_id"),instant(rs,"occurred_at"),rs.getString("event_type"),rs.getString("actor_username"),rs.getString("reason"),rs.getString("details"))).list();
        long count=jdbc.sql("SELECT COUNT(*) FROM security_access_control_change WHERE user_id=:u OR user_id IS NULL").param("u",userId).query(Long.class).single();
        return new Page<>(rows,count,page,size);
    }
    @Override public void setBlockDays(int days,String actor,String reason) {
        tx.executeWithoutResult(status->{
            int previous=jdbc.sql("SELECT block_days FROM security_access_control_settings WHERE singleton=TRUE FOR UPDATE").query(Integer.class).single();
            jdbc.sql("UPDATE security_access_control_settings SET block_days=:m,updated_at=:now WHERE singleton=TRUE").param("m",days).param("now",Timestamp.from(clock.instant())).update();
            change(null,"BLOCK_DURATION_CHANGED",actor,reason,"previousDays="+previous+";days="+days);
        });
    }
    @Override public void setQuota(long userId,int quota,String actor,String reason) {
        tx.executeWithoutResult(status->{
            lockUser(userId);
            long active=jdbc.sql("SELECT COUNT(*) FROM security_user_device_binding WHERE user_id=:u AND revoked_at IS NULL").param("u",userId).query(Long.class).single();
            if(active>quota) throw new AccessControlException(ACCESS_DEVICE_QUOTA_EXCEEDED);
            jdbc.sql("INSERT INTO security_user_device_quota(user_id,device_quota) VALUES(:u,:q) ON CONFLICT(user_id) DO UPDATE SET device_quota=EXCLUDED.device_quota,updated_at=CURRENT_TIMESTAMP")
                    .param("u",userId).param("q",quota).update();
            change(userId,"DEVICE_QUOTA_CHANGED",actor,reason,"deviceQuota="+quota);
        });
    }
    @Override public void bind(long userId,UUID deviceId,String actor,String reason) {
        tx.executeWithoutResult(status->{
            lockUser(userId);
            boolean active=jdbc.sql("SELECT EXISTS(SELECT 1 FROM security_device WHERE id=:d AND status='ACTIVE')").param("d",deviceId).query(Boolean.class).single();
            if(!active) throw new AccessControlException(ACCESS_DEVICE_NOT_ACTIVE);
            if(isDeviceAuthorized(userId,deviceId)) return;
            long count=jdbc.sql("SELECT COUNT(*) FROM security_user_device_binding WHERE user_id=:u AND revoked_at IS NULL").param("u",userId).query(Long.class).single();
            int quota=jdbc.sql("SELECT COALESCE((SELECT device_quota FROM security_user_device_quota WHERE user_id=:u),1)").param("u",userId).query(Integer.class).single();
            if(count>=quota) throw new AccessControlException(ACCESS_DEVICE_QUOTA_EXCEEDED);
            jdbc.sql("INSERT INTO security_user_device_binding(id,user_id,device_id) VALUES(:id,:u,:d)").param("id",UUID.randomUUID()).param("u",userId).param("d",deviceId).update();
            change(userId,"DEVICE_BOUND",actor,reason,"deviceId="+deviceId);
        });
    }
    @Override public void unbind(long userId,UUID bindingId,String actor,String reason) {
        tx.executeWithoutResult(status->{
            lockUser(userId);
            int changed=jdbc.sql("UPDATE security_user_device_binding SET revoked_at=GREATEST(:now,created_at) WHERE id=:id AND user_id=:u AND revoked_at IS NULL")
                    .param("now",Timestamp.from(clock.instant())).param("id",bindingId).param("u",userId).update();
            if(changed!=1) throw new AccessControlException(ACCESS_CONTROL_NOT_FOUND);
            change(userId,"DEVICE_UNBOUND",actor,reason,"bindingId="+bindingId);
        });
    }
    @Override public boolean certificateRequired(long userId) {
        return jdbc.sql("""
                SELECT COALESCE(c.certificate_required,false) FROM users u
                LEFT JOIN security_user_access_control c ON c.user_id=u.id WHERE u.id=:u
                """).param("u",userId).query(Boolean.class).optional()
                .orElseThrow(()->new AccessControlException(ACCESS_CONTROL_NOT_FOUND));
    }
    @Override public void setCertificateRequired(long userId,boolean required,String actor,String reason) {
        tx.executeWithoutResult(status->{
            lockUser(userId);
            boolean eligible=jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id
                        WHERE u.id=:u AND (u.enabled OR NOT :required))
                    """).param("u",userId).param("required",required).query(Boolean.class).single();
            if(!eligible) throw new AccessControlException(ACCESS_CONTROL_INVALID_REQUEST);
            if(certificateRequired(userId)==required) return;
            if(required && !jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM security_user_device_binding b
                        JOIN security_device d ON d.id=b.device_id
                        JOIN security_device_certificate c ON c.device_id=d.id
                        WHERE b.user_id=:u AND b.revoked_at IS NULL AND d.status='ACTIVE' AND d.hardware_approved
                        AND c.kind='DEVICE' AND c.revoked_at IS NULL
                        AND c.not_before<=CURRENT_TIMESTAMP AND c.expires_at>CURRENT_TIMESTAMP)
                    """).param("u",userId).query(Boolean.class).single())
                throw new AccessControlException(ACCESS_DEVICE_NOT_ACTIVE);
            state(userId,false);
            jdbc.sql("""
                    UPDATE security_user_access_control
                    SET certificate_required=:required,session_epoch=gen_random_uuid(),updated_at=CURRENT_TIMESTAMP WHERE user_id=:u
                    """).param("u",userId).param("required",required).update();
            change(userId,"INDIVIDUAL_DEVICE_POLICY_CHANGED",actor,reason,required?"CERTIFICATE_REQUIRED":"INHERIT_ROLE");
        });
    }
    @Override public void unblock(long userId,String actor,String reason) {
        tx.executeWithoutResult(status->{lockUser(userId); state(userId,false); newCycle(userId,"ACCOUNT_RELEASED",actor,reason);});
    }
    private void lockUser(long userId) {
        if(jdbc.sql("SELECT id FROM users WHERE id=:u FOR UPDATE").param("u",userId).query(Long.class).optional().isEmpty())
            throw new AccessControlException(ACCESS_CONTROL_NOT_FOUND);
    }
    private State state(long userId,boolean releaseExpired) {
        return state(userId,releaseExpired,clock.instant());
    }
    private State state(long userId,boolean releaseExpired,Instant now) {
        LocalDate today=now.atZone(ZoneId.of("America/Lima")).toLocalDate();
        jdbc.sql("INSERT INTO security_user_access_control(user_id,cycle_id,strike_day) VALUES(:u,:cycle,:day) ON CONFLICT(user_id) DO NOTHING")
                .param("u",userId).param("cycle",UUID.randomUUID()).param("day",java.sql.Date.valueOf(today)).update();
        State state=jdbc.sql("SELECT cycle_id,strike_count,blocked_until,strike_day FROM security_user_access_control WHERE user_id=:u")
                .param("u",userId).query((rs,n)->readState(rs,now)).single();
        if(releaseExpired && state.until()!=null && !state.blocked()) {
            newCycle(userId,"BLOCK_EXPIRED",null,"BLOCK_DURATION_COMPLETED",now);
            return state(userId,false,now);
        }
        if(!state.blocked() && !state.sameDay()) {
            jdbc.sql("UPDATE security_user_access_control SET strike_count=0,strike_day=:day,updated_at=:now WHERE user_id=:u")
                    .param("day",java.sql.Date.valueOf(today)).param("now",Timestamp.from(now)).param("u",userId).update();
            return new State(state.cycle(),0,state.until(),today,now);
        }
        return state;
    }
    private void newCycle(long userId,String type,String actor,String reason) {
        newCycle(userId,type,actor,reason,clock.instant());
    }
    private void newCycle(long userId,String type,String actor,String reason,Instant now) {
        UUID cycle=UUID.randomUUID();
        jdbc.sql("UPDATE security_user_access_control SET cycle_id=:cycle,strike_count=0,blocked_until=NULL,strike_day=:day,updated_at=:now WHERE user_id=:u")
                .param("cycle",cycle).param("day",java.sql.Date.valueOf(now.atZone(ZoneId.of("America/Lima")).toLocalDate()))
                .param("now",Timestamp.from(now)).param("u",userId).update();
        change(userId,type,actor,reason,"newCycleId="+cycle);
    }
    private void change(Long userId,String type,String actor,String reason,String details) {
        jdbc.sql("""
                INSERT INTO security_access_control_change(id,user_id,occurred_at,event_type,actor_user_id,actor_username,reason,details)
                VALUES(:id,:u,:now,:type,(SELECT id FROM users WHERE username=:actor),:actor,:reason,:details)
                """).param("id",UUID.randomUUID()).param("u",userId).param("now",Timestamp.from(clock.instant())).param("type",type)
                .param("actor",actor).param("reason",reason).param("details",details).update();
    }
    private State readState(ResultSet rs,Instant now) throws SQLException {
        return new State(rs.getObject("cycle_id",UUID.class),rs.getInt("strike_count"),instant(rs,"blocked_until"),
                rs.getDate("strike_day").toLocalDate(),now);
    }
    private record State(UUID cycle,int strikes,Instant until,LocalDate day,Instant now) {
        boolean blocked() { return until!=null && until.isAfter(now); }
        boolean sameDay() { return day.equals(now.atZone(ZoneId.of("America/Lima")).toLocalDate()); }
        Decision decision() { return new Decision(blocked(),blocked() || sameDay()?strikes:0,until); }
    }
    private static Timestamp timestamp(Instant instant) { return instant==null?null:Timestamp.from(instant); }
    private static Instant instant(ResultSet rs,String column) throws SQLException { Timestamp value=rs.getTimestamp(column); return value==null?null:value.toInstant(); }
    private static String pattern(String search) { return "%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%"; }
}

package com.paulfernandosr.possystembackend.accesscontrol.application;

import com.paulfernandosr.possystembackend.accesscontrol.domain.*;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.devicecertificate.DeviceCertificateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import java.util.*;
import java.util.function.Supplier;
import static com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException.Code.*;

@Service
public class AccessControlService {
    private final AccessControlRepository repository;
    private final boolean enabled;
    private final int threshold;
    private final int cooldownSeconds;
    private final boolean individualPolicyEnabled;
    private final DeviceCertificateService certificates;
    @Autowired public AccessControlService(AccessControlRepository repository,
            @Value("${security.access-control.enabled:false}") boolean enabled,
            @Value("${security.access-control.strike-threshold:3}") int threshold,
            @Value("${security.access-control.incident-cooldown-seconds:60}") int cooldownSeconds,
            @Value("${security.device-policy.enabled:false}") boolean devicePolicyEnabled,
            @Value("${security.device-policy.individual-enabled:false}") boolean individualEnabled,
            DeviceCertificateService certificates) {
        if(threshold<1 || threshold>100) throw new IllegalArgumentException("Invalid access control threshold");
        if(cooldownSeconds<1 || cooldownSeconds>3600) throw new IllegalArgumentException("Invalid access incident cooldown");
        this.repository=repository; this.enabled=enabled; this.threshold=threshold; this.cooldownSeconds=cooldownSeconds;
        this.individualPolicyEnabled=enabled && devicePolicyEnabled && individualEnabled;
        this.certificates=certificates;
    }

    public AccessControlService(AccessControlRepository repository,boolean enabled,int threshold,int cooldownSeconds) {
        this(repository,enabled,threshold,cooldownSeconds,false,false,null);
    }
    public AccessControlService(AccessControlRepository repository,boolean enabled,int threshold) { this(repository,enabled,threshold,60); }
    public boolean isEnabled() { return enabled; }
    public Status status() { return new Status(enabled,threshold,1,enabled?run(repository::blockDays):1,"America/Lima",individualPolicyEnabled); }
    public boolean certificateRequired(long id) {
        requireIndividualPolicy(); id(id);
        return run(()->repository.certificateRequired(id));
    }
    public void setCertificateRequired(long id,Boolean required,String actor,String reason) {
        requireIndividualPolicy(); id(id); actor(actor);
        String clean=reason(reason);
        if(required==null || clean.length()<10) invalid();
        if(required && !certificates.isReady()) throw new AccessControlException(ACCESS_CONTROL_UNAVAILABLE);
        run(()->{ repository.setCertificateRequired(id,required,actor,clean); return null; });
    }
    public void checkAccount(User user) {
        if(!enabled) return;
        validateUser(user);
        if(run(()->repository.checkAccount(user.getId())).blocked()) throw new AccessControlException(ACCESS_ACCOUNT_BLOCKED);
    }
    /** Only call after validating credentials, and only for an unauthorized PC, never for city or an expired owned certificate. */
    public void rejectUnauthorizedDevice(User user, UUID trustedDeviceId, UUID trustedAttemptId) {
        rejectUnauthorizedDevice(user,trustedDeviceId,trustedAttemptId,null);
    }
    public void rejectUnauthorizedDevice(User user, UUID trustedDeviceId, UUID trustedAttemptId,String originKey) {
        requireEnabled(); validateUser(user);
        if(trustedAttemptId==null) throw new AccessControlException(ACCESS_CONTROL_INVALID_REQUEST);
        if(originKey!=null&&!originKey.matches("[a-f0-9]{64}")) throw new AccessControlException(ACCESS_CONTROL_INVALID_REQUEST);
        Decision decision=run(()->repository.recordUnauthorizedDevice(user.getId(),trustedDeviceId,trustedAttemptId,threshold,originKey,cooldownSeconds));
        throw new AccessControlException(decision.blocked()?ACCESS_ACCOUNT_BLOCKED:ACCESS_DEVICE_NOT_AUTHORIZED);
    }
    public boolean isDeviceAuthorized(User user,UUID deviceId) {
        requireEnabled(); validateUser(user);
        return deviceId!=null && run(()->repository.isDeviceAuthorized(user.getId(),deviceId));
    }
    public Page<UserControl> users(String search,int page,int size) { requireEnabled(); validatePage(page,size); return run(()->repository.users(search(search),page,size)); }
    public UserControl user(long id) { requireEnabled(); id(id); return run(()->repository.user(id)); }
    public Page<DeviceRow> devices(String search,int page,int size) { requireEnabled(); validatePage(page,size); return run(()->repository.devices(search(search),page,size)); }
    public List<Binding> bindings(long id) { requireEnabled(); id(id); return run(()->repository.bindings(id)); }
    public Page<Strike> strikes(long id,int page,int size) { requireEnabled(); id(id); validatePage(page,size); return run(()->repository.strikes(id,page,size)); }
    public Page<Change> history(long id,int page,int size) { requireEnabled(); id(id); validatePage(page,size); return run(()->repository.history(id,page,size)); }
    public void setBlockDays(int days,String actor,String reason) {
        requireEnabled(); if(days<1||days>3650) invalid(); String clean=reason(reason); actor(actor);
        run(()->{ repository.setBlockDays(days,actor,clean); return null; });
    }
    public void setQuota(long id,int quota,String actor,String reason) {
        requireEnabled(); id(id); if(quota<1||quota>100) invalid(); String clean=reason(reason); actor(actor);
        run(()->{ repository.setQuota(id,quota,actor,clean); return null; });
    }
    public void bind(long id,UUID deviceId,String actor,String reason) {
        requireEnabled(); id(id); if(deviceId==null) invalid(); String clean=reason(reason); actor(actor);
        run(()->{ repository.bind(id,deviceId,actor,clean); return null; });
    }
    public void unbind(long id,UUID bindingId,String actor,String reason) {
        requireEnabled(); id(id); if(bindingId==null) invalid(); String clean=reason(reason); actor(actor);
        run(()->{ repository.unbind(id,bindingId,actor,clean); return null; });
    }
    public void unblock(long id,String actor,String reason) {
        requireEnabled(); id(id); String clean=reason(reason); actor(actor);
        run(()->{ repository.unblock(id,actor,clean); return null; });
    }
    private void requireEnabled() { if(!enabled) throw new AccessControlException(ACCESS_CONTROL_DISABLED); }
    private void requireIndividualPolicy() { if(!individualPolicyEnabled) throw new AccessControlException(ACCESS_CONTROL_DISABLED); }
    private static void validateUser(User user) { if(user==null||user.getId()==null||!user.isEnabled()) throw new AccessControlException(ACCESS_DEVICE_NOT_AUTHORIZED); }
    private static void id(long id) { if(id<=0) invalid(); }
    private static void actor(String actor) { if(actor==null||actor.isBlank()||actor.length()>160) invalid(); }
    private static void validatePage(int page,int size) { if(page<0||page>100000||size<1||size>100) invalid(); }
    private static String search(String search) { if(search==null) return ""; if(search.length()>120) invalid(); return search.trim(); }
    private static String reason(String reason) {
        if(reason==null) { invalid(); return null; }
        String clean=reason.replaceAll("[\\p{Cntrl}\\r\\n]"," ").trim();
        if(clean.length()<5||clean.length()>300) invalid(); return clean;
    }
    private static void invalid() { throw new AccessControlException(ACCESS_CONTROL_INVALID_REQUEST); }
    private <T> T run(Supplier<T> call) {
        try { return call.get(); }
        catch(DataAccessException|TransactionException ex) { throw new AccessControlException(ACCESS_CONTROL_UNAVAILABLE); }
    }
}

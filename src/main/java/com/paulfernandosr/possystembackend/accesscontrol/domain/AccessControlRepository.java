package com.paulfernandosr.possystembackend.accesscontrol.domain;

import java.util.List;
import java.util.UUID;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlModels.*;

public interface AccessControlRepository {
    int blockDays();
    Decision checkAccount(long userId);
    Decision recordUnauthorizedDevice(long userId, UUID deviceId, UUID attemptId, int threshold, String originKey, int cooldownSeconds);
    boolean isDeviceAuthorized(long userId, UUID deviceId);
    Page<UserControl> users(String search, int page, int size);
    UserControl user(long userId);
    boolean certificateRequired(long userId);
    void setCertificateRequired(long userId,boolean required,String actor,String reason);
    Page<DeviceRow> devices(String search, int page, int size);
    List<Binding> bindings(long userId);
    Page<Strike> strikes(long userId, int page, int size);
    Page<Change> history(long userId, int page, int size);
    void setBlockDays(int days, String actor, String reason);
    void setQuota(long userId, int quota, String actor, String reason);
    void bind(long userId, UUID deviceId, String actor, String reason);
    void unbind(long userId, UUID bindingId, String actor, String reason);
    void unblock(long userId, String actor, String reason);
}

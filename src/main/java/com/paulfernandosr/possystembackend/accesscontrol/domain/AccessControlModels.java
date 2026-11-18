package com.paulfernandosr.possystembackend.accesscontrol.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccessControlModels {
    private AccessControlModels() {}
    public record Status(boolean enabled, int strikeThreshold, int defaultDeviceQuota, int blockDays, String timezone,
                         boolean individualPolicyEnabled) {
        public Status(boolean enabled,int strikeThreshold,int defaultDeviceQuota,int blockDays,String timezone) {
            this(enabled,strikeThreshold,defaultDeviceQuota,blockDays,timezone,false);
        }
    }
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record UserControl(long userId, String username, String roleName, boolean enabled,
                              int deviceQuota, long activeDevices, int strikes, UUID cycleId,
                              Instant blockedUntil, boolean blocked) {}
    public record DeviceRow(UUID id, String displayName, String platform, String status, Instant createdAt) {}
    public record Binding(UUID id, long userId, UUID deviceId, String displayName, String platform,
                          String deviceStatus, Instant createdAt, Instant revokedAt) {}
    public record Strike(UUID id, long userId, UUID cycleId, Instant occurredAt, UUID deviceId,
                         String reason, int threshold, Instant blockedUntil) {}
    public record Change(UUID id, long userId, Instant occurredAt, String eventType,
                         String actorUsername, String reason, String details) {}
    public record Decision(boolean blocked, int strikes, Instant blockedUntil) {}
}

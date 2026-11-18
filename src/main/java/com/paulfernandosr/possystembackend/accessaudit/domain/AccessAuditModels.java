package com.paulfernandosr.possystembackend.accessaudit.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccessAuditModels {
    private AccessAuditModels() {}

    public record AuthenticationContext(String certificateState, String fingerprintSha256, String serialHex,
                                        UUID deviceId, String deviceName, String failureReason) {
        public AuthenticationContext withFailure(String reason) {
            return new AuthenticationContext(certificateState,fingerprintSha256,serialHex,deviceId,deviceName,reason);
        }
    }
    public record Metadata(String peerAddress, String userAgent, UUID correlationId,
                           com.paulfernandosr.possystembackend.trustedclient.GeoLocationService.Location location,
                           AuthenticationContext authentication) {
        public Metadata(String peerAddress,String userAgent,UUID correlationId) { this(peerAddress,userAgent,correlationId,null,null); }
        public Metadata(String peerAddress,String userAgent,UUID correlationId,
                com.paulfernandosr.possystembackend.trustedclient.GeoLocationService.Location location) {
            this(peerAddress,userAgent,correlationId,location,null);
        }
    }
    public record SessionReference(String key, String username) {}
    public record Event(UUID id, Instant occurredAt, Instant lastOccurredAt, String eventType,
                        String outcome, String source, String username, String attemptedUsername,
                        String roleName, UUID sessionId, String actorUsername, String reason,
                        String peerAddress, String userAgent, UUID correlationId, long occurrences,
                        Long userId, Long actorUserId, String category, AuthenticationContext authentication) {
        public Event(UUID id,Instant occurredAt,Instant lastOccurredAt,String eventType,String outcome,String source,
                String username,String attemptedUsername,String roleName,UUID sessionId,String actorUsername,String reason,
                String peerAddress,String userAgent,UUID correlationId,long occurrences,Long userId,Long actorUserId) {
            this(id,occurredAt,lastOccurredAt,eventType,outcome,source,username,attemptedUsername,roleName,sessionId,
                    actorUsername,reason,peerAddress,userAgent,correlationId,occurrences,userId,actorUserId,null,null);
        }
    }
    public record SessionRow(UUID id, String username, String roleName, Instant startedAt,
                             Instant lastSeenAt, Instant expiresAt, String state, Instant endedAt,
                             String endReason, String peerAddress, String userAgent, Long userId) {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record Hour(int hour, long granted, long rejected) {}
    public record Daily(String day, long granted, long rejected, long users) {}
    public record Summary(long grantedLogins, long rejectedAttempts, long distinctUsers,
                          long activeSessionsNow, long outsideBusinessHours, List<Hour> hours,
                          List<Daily> days) {}
    public record Status(boolean enabled, String timezone, Instant firstAvailableEvent,
                         int maxRangeDays, int maxExportRows, boolean certificateContextReady,
                         String addressSource, int failureAlertThreshold, boolean authenticationContextReady) {
        public Status(boolean enabled,String timezone,Instant firstAvailableEvent,int maxRangeDays,int maxExportRows,
                boolean certificateContextReady,String addressSource,int failureAlertThreshold) {
            this(enabled,timezone,firstAvailableEvent,maxRangeDays,maxExportRows,certificateContextReady,addressSource,failureAlertThreshold,false);
        }
    }
}

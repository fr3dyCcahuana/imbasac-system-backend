package com.paulfernandosr.possystembackend.devicecertificate;

import java.time.Instant;
import java.util.UUID;
public final class CertificateModels {
    private CertificateModels() {}
    public record Status(boolean enabled,boolean proxyConfigured,boolean geoipEnabled,boolean hardwareApprovalRequired,boolean signerConfigured,
                         long pendingCaRevocations,long caRevocationsNeedingReview,boolean registrationEnabled,boolean enrollmentEnabled) {
        public Status(boolean enabled,boolean proxyConfigured,boolean geoipEnabled,boolean hardwareApprovalRequired,boolean signerConfigured,long pendingCaRevocations,long caRevocationsNeedingReview) {this(enabled,proxyConfigured,geoipEnabled,hardwareApprovalRequired,signerConfigured,pendingCaRevocations,caRevocationsNeedingReview,enabled,enabled);}
        public Status(boolean enabled,boolean proxyConfigured,boolean geoipEnabled,boolean hardwareApprovalRequired,boolean signerConfigured) {this(enabled,proxyConfigured,geoipEnabled,hardwareApprovalRequired,signerConfigured,0,0);}
        public Status(boolean enabled,boolean proxyConfigured,boolean geoipEnabled,boolean hardwareApprovalRequired) {this(enabled,proxyConfigured,geoipEnabled,hardwareApprovalRequired,false,0,0);}
    }
    public record CertificateRow(UUID id,UUID deviceId,String fingerprintSha256,String serialHex,
                                 Instant notBefore,Instant expiresAt,Instant revokedAt,boolean hardwareApproved,String hardwareEvidenceReference) {}
    public record Enrollment(UUID id,UUID deviceId,Instant createdAt,String status,String publicKeySha256,String csrPem) {}
    public record RolePolicy(long roleId,String roleName,String mode) {}
    public record RegisterEnrollment(UUID deviceId,String csrPem) {}
    public record ImportCertificate(UUID deviceId,String certificatePem,String reason) {}
    public record HardwareApproval(boolean approved,String evidenceReference,String reason) {}
    public record PolicyChange(String mode,String reason) {}
    public record Reason(String reason) {}
    public record NewDevice(String displayName,String platform,String reason) {}
}

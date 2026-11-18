package com.paulfernandosr.possystembackend.devicecertificate;
import java.util.UUID;
public interface CertificateSigner {
    boolean isConfigured();
    String sign(UUID requestId,UUID deviceId,String csrPem);
    default void revoke(UUID requestId,UUID deviceId,String certificatePem) {
        throw new CertificateException(CertificateException.Code.CERTIFICATE_SIGNER_UNAVAILABLE);
    }
}

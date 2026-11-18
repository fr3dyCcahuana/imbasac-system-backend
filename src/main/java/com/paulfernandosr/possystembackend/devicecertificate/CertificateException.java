package com.paulfernandosr.possystembackend.devicecertificate;
public class CertificateException extends RuntimeException {
    public enum Code { CERTIFICATES_DISABLED,DEVICE_REGISTRATION_DISABLED,CERTIFICATE_UNAVAILABLE,CERTIFICATE_INVALID,
        CERTIFICATE_NOT_FOUND,CERTIFICATE_DEVICE_NOT_AUTHORIZED,CERTIFICATE_HARDWARE_NOT_APPROVED,
        CERTIFICATE_ENROLLMENT_REQUIRED,CERTIFICATE_REQUEST_INVALID,CERTIFICATE_PROXY_NOT_READY,CERTIFICATE_SIGNER_UNAVAILABLE }
    private final Code code;
    public CertificateException(Code code) { super(code.name()); this.code=code; }
    public Code getCode() { return code; }
}

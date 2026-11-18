package com.paulfernandosr.possystembackend.accesscontrol.domain;

public class AccessControlException extends RuntimeException {
    public enum Code { ACCESS_CONTROL_DISABLED, ACCESS_CONTROL_UNAVAILABLE, ACCESS_ACCOUNT_BLOCKED,
        ACCESS_DEVICE_NOT_AUTHORIZED, ACCESS_CONTROL_INVALID_REQUEST, ACCESS_CONTROL_NOT_FOUND,
        ACCESS_DEVICE_QUOTA_EXCEEDED, ACCESS_DEVICE_NOT_ACTIVE }
    private final Code code;
    public AccessControlException(Code code) { super(code.name()); this.code=code; }
    public Code getCode() { return code; }
}

package com.paulfernandosr.possystembackend.driverlicense.assisted;

public class MtcAssistedException extends RuntimeException {
    private final String code;
    private final int status;
    public MtcAssistedException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public String code() { return code; }
    public int status() { return status; }
}

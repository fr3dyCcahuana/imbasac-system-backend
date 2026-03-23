package com.paulfernandosr.possystembackend.guideremission.domain.exception;

public class GuideRemissionIntegrationException extends RuntimeException {
    public GuideRemissionIntegrationException(String message) {
        super(message);
    }

    public GuideRemissionIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

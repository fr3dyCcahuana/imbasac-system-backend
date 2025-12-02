package com.paulfernandosr.possystembackend.common.domain.port.output;

public interface PasswordValidationPort {
    boolean isPasswordValid(String password);
}

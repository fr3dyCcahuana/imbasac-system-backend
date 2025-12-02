package com.paulfernandosr.possystembackend.security.domain.port.output;

public interface AuthenticationPort {
    boolean authenticate(String username, String password);
}

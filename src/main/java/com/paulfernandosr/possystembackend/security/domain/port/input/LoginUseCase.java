package com.paulfernandosr.possystembackend.security.domain.port.input;

import com.paulfernandosr.possystembackend.security.domain.Session;

public interface LoginUseCase {
    Session login(String username, String password);
}

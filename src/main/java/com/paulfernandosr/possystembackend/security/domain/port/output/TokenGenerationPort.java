package com.paulfernandosr.possystembackend.security.domain.port.output;

import com.paulfernandosr.possystembackend.security.domain.Token;

public interface TokenGenerationPort {
    Token generateToken(String username);
}

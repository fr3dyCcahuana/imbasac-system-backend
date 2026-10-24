package com.paulfernandosr.possystembackend.security.domain.port.output;

import com.paulfernandosr.possystembackend.security.domain.Token;
import java.util.UUID;

public interface TokenGenerationPort {
    Token generateToken(String username, UUID sessionId);
}

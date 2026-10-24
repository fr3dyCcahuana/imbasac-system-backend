package com.paulfernandosr.possystembackend.security.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.security.domain.Token;
import com.paulfernandosr.possystembackend.security.domain.port.output.TokenGenerationPort;
import com.paulfernandosr.possystembackend.security.infrastructure.JsonWebTokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JsonWebTokenGenerationAdapter implements TokenGenerationPort {
    private final JsonWebTokenUtils jsonWebTokenUtils;

    @Override
    public Token generateToken(String username, UUID sessionId) {
        return jsonWebTokenUtils.generateToken(username, sessionId);
    }
}

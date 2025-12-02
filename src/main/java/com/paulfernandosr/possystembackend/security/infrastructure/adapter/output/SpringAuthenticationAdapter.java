package com.paulfernandosr.possystembackend.security.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.security.domain.port.output.AuthenticationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringAuthenticationAdapter implements AuthenticationPort {
    private final AuthenticationManager authenticationManager;

    @Override
    public boolean authenticate(String username, String password) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, password);
        return authenticationManager.authenticate(authentication).isAuthenticated();
    }
}

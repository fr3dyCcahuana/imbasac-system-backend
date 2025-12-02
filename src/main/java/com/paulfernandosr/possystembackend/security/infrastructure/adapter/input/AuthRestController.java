package com.paulfernandosr.possystembackend.security.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.security.domain.Session;
import com.paulfernandosr.possystembackend.security.domain.port.input.LogoutUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.input.RefreshSessionUseCase;
import com.paulfernandosr.possystembackend.security.domain.port.input.LoginUseCase;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.request.LoginRequest;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.request.RefreshTokenRequest;
import com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.response.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthRestController {
    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Session session = loginUseCase.login(request.getUsername(), request.getPassword());

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(session.getToken().value())
                .refreshToken(session.getId())
                .tokenType(session.getToken().type())
                .expiresIn(session.getToken().expiresIn())
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(@Valid @RequestBody RefreshTokenRequest request) {
        logoutUseCase.logout(UUID.fromString(request.getRefreshToken()));

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        Session session = refreshSessionUseCase.refreshSession(UUID.fromString(request.getRefreshToken()));

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(session.getToken().value())
                .refreshToken(session.getId())
                .tokenType(session.getToken().type())
                .expiresIn(session.getToken().expiresIn())
                .build());
    }
}

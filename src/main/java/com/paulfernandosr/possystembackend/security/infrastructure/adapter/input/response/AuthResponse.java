package com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private UUID refreshToken;
    private String tokenType;
    private long expiresIn;

    public AuthResponse(String accessToken) {
        this.accessToken = accessToken;
    }
}

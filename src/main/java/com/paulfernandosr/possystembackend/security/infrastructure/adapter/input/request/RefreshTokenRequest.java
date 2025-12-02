package com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {
    @NotEmpty
    private String refreshToken;
}

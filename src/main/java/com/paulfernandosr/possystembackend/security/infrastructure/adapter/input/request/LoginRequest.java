package com.paulfernandosr.possystembackend.security.infrastructure.adapter.input.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotEmpty
    private String username;
    @NotEmpty
    private String password;
}

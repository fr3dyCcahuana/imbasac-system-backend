package com.paulfernandosr.possystembackend.security.domain;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    private UUID id;
    private Token token;
    private String username;

    public Session(UUID id, String username) {
        this.id = id;
        this.username = username;
    }
}

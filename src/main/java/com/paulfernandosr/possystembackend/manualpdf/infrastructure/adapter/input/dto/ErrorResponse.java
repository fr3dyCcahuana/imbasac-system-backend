package com.paulfernandosr.possystembackend.manualpdf.infrastructure.adapter.input.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
        int status,
        String error,
        LocalDateTime timestamp
) {
}

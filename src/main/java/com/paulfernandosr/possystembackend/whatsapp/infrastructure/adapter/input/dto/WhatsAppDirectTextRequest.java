package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;

public record WhatsAppDirectTextRequest(
        @NotBlank String waId,
        @NotBlank String body
) {}

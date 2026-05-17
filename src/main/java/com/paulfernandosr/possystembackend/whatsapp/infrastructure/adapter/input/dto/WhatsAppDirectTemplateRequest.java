package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;

public record WhatsAppDirectTemplateRequest(
        @NotBlank String waId,
        @NotBlank String templateName,
        String languageCode
) {}

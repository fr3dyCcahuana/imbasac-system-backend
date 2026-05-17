package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;

public record WhatsAppSendTextRequest(
        @NotBlank String body
) {}

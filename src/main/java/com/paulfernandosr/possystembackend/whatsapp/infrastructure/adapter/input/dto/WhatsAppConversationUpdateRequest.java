package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input.dto;

public record WhatsAppConversationUpdateRequest(
        String status,
        String automationMode,
        String assignedTo
) {}

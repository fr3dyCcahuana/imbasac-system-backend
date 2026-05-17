package com.paulfernandosr.possystembackend.whatsapp.domain.port.input;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppMessage;

public interface SendWhatsAppMessageUseCase {
    WhatsAppMessage sendTextToConversation(Long conversationId, String body);
    WhatsAppMessage sendTextToWaId(String waId, String body);
    WhatsAppMessage sendTemplateToWaId(String waId, String templateName, String languageCode);
}

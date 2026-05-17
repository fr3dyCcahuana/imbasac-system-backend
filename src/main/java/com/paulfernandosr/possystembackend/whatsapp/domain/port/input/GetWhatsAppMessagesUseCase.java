package com.paulfernandosr.possystembackend.whatsapp.domain.port.input;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppMessage;

import java.util.List;

public interface GetWhatsAppMessagesUseCase {
    List<WhatsAppMessage> findByConversation(Long conversationId, int page, int size);
    long countByConversation(Long conversationId);
}

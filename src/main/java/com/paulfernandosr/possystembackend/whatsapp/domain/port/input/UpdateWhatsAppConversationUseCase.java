package com.paulfernandosr.possystembackend.whatsapp.domain.port.input;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppEnums;

public interface UpdateWhatsAppConversationUseCase {
    void updateStatus(Long conversationId, WhatsAppEnums.ConversationStatus status);
    void updateAutomationMode(Long conversationId, WhatsAppEnums.AutomationMode mode);
    void assignTo(Long conversationId, String username);
}

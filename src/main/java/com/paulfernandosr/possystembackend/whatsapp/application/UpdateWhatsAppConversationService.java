package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppEnums;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.input.UpdateWhatsAppConversationUseCase;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateWhatsAppConversationService implements UpdateWhatsAppConversationUseCase {
    private final WhatsAppConversationRepository repository;

    @Override
    public void updateStatus(Long conversationId, WhatsAppEnums.ConversationStatus status) {
        repository.updateStatus(conversationId, status);
    }

    @Override
    public void updateAutomationMode(Long conversationId, WhatsAppEnums.AutomationMode mode) {
        repository.updateAutomationMode(conversationId, mode);
    }

    @Override
    public void assignTo(Long conversationId, String username) {
        repository.assignTo(conversationId, username);
    }
}

package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;

import java.util.List;
import java.util.Optional;

public interface WhatsAppConversationRepository {
    WhatsAppConversation findOrCreateOpenConversation(WhatsAppContact contact);
    Optional<WhatsAppConversation> findById(Long id);
    List<WhatsAppConversationSummary> findPage(WhatsAppConversationFilter filter);
    long count(WhatsAppConversationFilter filter);
    void updateLastMessage(Long conversationId, String preview, java.time.OffsetDateTime messageAt);
    void updateStatus(Long conversationId, WhatsAppEnums.ConversationStatus status);
    void updateAutomationMode(Long conversationId, WhatsAppEnums.AutomationMode mode);
    void updateConversationState(Long conversationId, WhatsAppEnums.ConversationState state);
    void assignTo(Long conversationId, String username);
    void linkProforma(Long conversationId, Long proformaId);
}

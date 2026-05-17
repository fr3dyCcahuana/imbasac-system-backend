package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;

import java.util.List;
import java.util.Optional;

public interface WhatsAppMessageRepository {
    WhatsAppMessage save(WhatsAppMessage message);
    List<WhatsAppMessage> findByConversation(Long conversationId, int page, int size);
    List<WhatsAppMessage> findByConversationAfterId(Long conversationId, Long afterId, int size);
    Long findMaxIdByConversation(Long conversationId);
    long countByConversation(Long conversationId);
    Optional<WhatsAppMessage> findByWaMessageId(String waMessageId);
    void updateStatus(String waMessageId, WhatsAppEnums.MessageStatus status, String errorCode, String errorTitle, String errorDetails, String rawPayload);
}

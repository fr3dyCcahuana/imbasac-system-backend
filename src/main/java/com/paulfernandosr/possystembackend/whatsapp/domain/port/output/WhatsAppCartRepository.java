package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;

import java.util.List;
import java.util.Optional;

public interface WhatsAppCartRepository {
    WhatsAppCart findOrCreateOpenCart(Long conversationId);
    Optional<WhatsAppCart> findOpenByConversationId(Long conversationId);
    void setCurrentProductSuggestion(Long cartId, Long suggestionId);
    void addItem(WhatsAppCartItem item);
    List<WhatsAppCartItem> findItems(Long cartId);
    void setCustomerDocument(Long cartId, String documentType, String documentNumber);
    void setCustomerName(Long cartId, String customerName);
    void updateStatus(Long cartId, WhatsAppEnums.CartStatus status);
    void linkProforma(Long cartId, Long proformaId);
    void cancelOpenCarts(Long conversationId);
}

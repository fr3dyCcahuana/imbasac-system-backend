package com.paulfernandosr.possystembackend.whatsapp.domain.port.input;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;

import java.util.List;

public interface GetWhatsAppConversationsUseCase {
    List<WhatsAppConversationSummary> findPage(WhatsAppConversationFilter filter);
    long count(WhatsAppConversationFilter filter);
}

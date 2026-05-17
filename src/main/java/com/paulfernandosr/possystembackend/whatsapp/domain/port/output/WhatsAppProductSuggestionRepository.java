package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;

import java.util.List;
import java.util.Optional;

public interface WhatsAppProductSuggestionRepository {
    void replaceSuggestions(Long conversationId, List<WhatsAppProductSearchResult> products, String rawSearchText);
    Optional<WhatsAppProductSuggestion> findLatestByPosition(Long conversationId, int position);
    Optional<WhatsAppProductSuggestion> findById(Long id);
    List<WhatsAppProductSuggestion> findLatest(Long conversationId, int limit);
}

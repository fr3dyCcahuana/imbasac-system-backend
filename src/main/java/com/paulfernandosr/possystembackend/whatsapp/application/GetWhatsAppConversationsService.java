package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.input.GetWhatsAppConversationsUseCase;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetWhatsAppConversationsService implements GetWhatsAppConversationsUseCase {
    private final WhatsAppConversationRepository repository;

    @Override
    public List<WhatsAppConversationSummary> findPage(WhatsAppConversationFilter filter) {
        normalize(filter);
        return repository.findPage(filter);
    }

    @Override
    public long count(WhatsAppConversationFilter filter) {
        normalize(filter);
        return repository.count(filter);
    }

    private void normalize(WhatsAppConversationFilter filter) {
        if (filter.getPage() < 0) filter.setPage(0);
        if (filter.getSize() <= 0 || filter.getSize() > 100) filter.setSize(20);
    }
}

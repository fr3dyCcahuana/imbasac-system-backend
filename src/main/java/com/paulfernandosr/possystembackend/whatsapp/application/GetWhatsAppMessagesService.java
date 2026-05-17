package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppMessage;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.input.GetWhatsAppMessagesUseCase;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetWhatsAppMessagesService implements GetWhatsAppMessagesUseCase {
    private final WhatsAppMessageRepository repository;

    @Override
    public List<WhatsAppMessage> findByConversation(Long conversationId, int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0 || size > 200) size = 50;
        return repository.findByConversation(conversationId, page, size);
    }

    @Override
    public long countByConversation(Long conversationId) {
        return repository.countByConversation(conversationId);
    }
}

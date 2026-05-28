package com.paulfernandosr.possystembackend.whatsappcenter.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.WhatsAppAgentClient;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.PostgresWhatsAppCenterQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class WhatsAppCenterConversationService {
    private final PostgresWhatsAppCenterQueryRepository repository;
    private final WhatsAppAgentClient agentClient;

    public PageResponse<ConversationSummaryResponse> conversations(int page, int size, String q, String status, String from, String to, String waId, String phoneNumber, Long proformaId, Long sellerId) {
        return repository.conversations(page, size, q, status, from, to, waId, phoneNumber, proformaId, sellerId);
    }

    public ConversationDetailResponse conversation(Long conversationId) {
        return repository.conversation(conversationId);
    }

    public PageResponse<MessageResponse> messages(Long conversationId, int page, int size) {
        return repository.messages(conversationId, page, size);
    }

    public PageResponse<ConversationEventResponse> events(Long conversationId, int page, int size) {
        return repository.conversationEvents(conversationId, page, size);
    }

    public PageResponse<AgentRunResponse> agentRuns(Long conversationId, int page, int size) {
        return repository.agentRuns(conversationId, page, size);
    }

    public PageResponse<ProductSuggestionResponse> productSuggestions(Long conversationId, int page, int size) {
        return repository.productSuggestions(conversationId, page, size);
    }

    public PageResponse<QuoteDraftEventResponse> quoteEvents(Long conversationId, int page, int size) {
        return repository.quoteEvents(conversationId, page, size);
    }

    public JsonNode setManualMode(Long conversationId, ManualModeRequest request) {
        return agentClient.setManualMode(conversationId, request);
    }

    public JsonNode sendText(Long conversationId, SendManualMessageRequest request) {
        return agentClient.sendText(conversationId, request);
    }

    public JsonNode sendMedia(Long conversationId, MultipartFile file, String caption) {
        return agentClient.sendMedia(conversationId, file, caption);
    }
}

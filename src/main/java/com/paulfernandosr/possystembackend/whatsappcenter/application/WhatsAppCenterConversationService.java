package com.paulfernandosr.possystembackend.whatsappcenter.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.WhatsAppAgentClient;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.PostgresWhatsAppCenterQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
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

    public ConversationSummaryResponse startConversation(StartConversationRequest request) {
        JsonNode response = agentClient.startConversation(request);
        Long conversationId = extractConversationId(response);
        if (conversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El agente no devolvio el id de la conversacion");
        }
        ConversationSummaryResponse conversation = repository.conversationSummary(conversationId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "La conversacion fue creada, pero no se pudo leer desde el ERP");
        }
        return conversation;
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

    private Long extractConversationId(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode direct = response.get("conversationId");
        if (direct != null && direct.canConvertToLong()) {
            return direct.asLong();
        }
        JsonNode nested = response.path("conversation").path("conversationId");
        if (nested.canConvertToLong()) {
            return nested.asLong();
        }
        JsonNode legacy = response.path("conversation").path("id");
        return legacy.canConvertToLong() ? legacy.asLong() : null;
    }
}

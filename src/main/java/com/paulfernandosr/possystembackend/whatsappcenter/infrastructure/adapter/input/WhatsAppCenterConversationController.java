package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.application.WhatsAppCenterConversationService;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/whatsapp-center/conversations")
@RequiredArgsConstructor
public class WhatsAppCenterConversationController {
    private final WhatsAppCenterConversationService service;

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ConversationSummaryResponse>>> conversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String waId,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) Long proformaId,
            @RequestParam(required = false) Long sellerId
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.conversations(page, size, q, status, from, to, waId, phoneNumber, proformaId, sellerId)));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<SuccessResponse<ConversationDetailResponse>> conversation(@PathVariable Long conversationId) {
        return ResponseEntity.ok(SuccessResponse.ok(service.conversation(conversationId)));
    }

    @PostMapping("/start")
    public ResponseEntity<SuccessResponse<ConversationSummaryResponse>> startConversation(
            @RequestBody StartConversationRequest request
    ) {
        return ResponseEntity.status(201).body(SuccessResponse.created(service.startConversation(request)));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<SuccessResponse<PageResponse<MessageResponse>>> messages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.messages(conversationId, page, size)));
    }

    @GetMapping("/{conversationId}/events")
    public ResponseEntity<SuccessResponse<PageResponse<ConversationEventResponse>>> events(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.events(conversationId, page, size)));
    }

    @GetMapping("/{conversationId}/agent-runs")
    public ResponseEntity<SuccessResponse<PageResponse<AgentRunResponse>>> agentRuns(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.agentRuns(conversationId, page, size)));
    }

    @GetMapping("/{conversationId}/product-suggestions")
    public ResponseEntity<SuccessResponse<PageResponse<ProductSuggestionResponse>>> productSuggestions(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.productSuggestions(conversationId, page, size)));
    }

    @GetMapping("/{conversationId}/quote-events")
    public ResponseEntity<SuccessResponse<PageResponse<QuoteDraftEventResponse>>> quoteEvents(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.quoteEvents(conversationId, page, size)));
    }

    @PostMapping("/{conversationId}/manual-mode")
    public ResponseEntity<SuccessResponse<JsonNode>> setManualMode(
            @PathVariable Long conversationId,
            @RequestBody ManualModeRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.setManualMode(conversationId, request)));
    }

    @PostMapping("/{conversationId}/messages/text")
    public ResponseEntity<SuccessResponse<JsonNode>> sendText(
            @PathVariable Long conversationId,
            @RequestBody JsonNode request
    ) {
        String body = textValue(request, "body");
        if (body == null) {
            body = textValue(request, "text");
        }
        if (body == null) {
            body = textValue(request, "message");
        }
        if (body == null && request != null && request.isTextual()) {
            body = request.asText();
        }
        body = body == null ? "" : body.trim();
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje no puede estar vacio");
        }
        SendManualMessageRequest payload = SendManualMessageRequest.builder().body(body).build();
        return ResponseEntity.status(201).body(SuccessResponse.created(service.sendText(conversationId, payload)));
    }

    @PostMapping(value = "/{conversationId}/messages/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<JsonNode>> sendMedia(
            @PathVariable Long conversationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String caption
    ) {
        return ResponseEntity.status(201).body(SuccessResponse.created(service.sendMedia(conversationId, file, caption)));
    }

    @PostMapping("/{conversationId}/video-call/invite")
    public ResponseEntity<SuccessResponse<JsonNode>> inviteVideoCall(
            @PathVariable Long conversationId,
            @RequestBody(required = false) VideoCallInviteRequest request
    ) {
        return ResponseEntity.status(201).body(SuccessResponse.created(service.inviteVideoCall(conversationId, request)));
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asText();
    }
}

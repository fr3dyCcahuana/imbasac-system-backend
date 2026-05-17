package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.input.*;
import com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/whatsapp")
public class WhatsAppConversationRestController {
    private final GetWhatsAppConversationsUseCase getConversationsUseCase;
    private final GetWhatsAppMessagesUseCase getMessagesUseCase;
    private final SendWhatsAppMessageUseCase sendMessageUseCase;
    private final UpdateWhatsAppConversationUseCase updateConversationUseCase;

    @GetMapping("/conversations")
    public ResponseEntity<SuccessResponse<List<WhatsAppConversationSummary>>> getConversations(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        WhatsAppConversationFilter filter = WhatsAppConversationFilter.builder()
                .query(query)
                .status(status)
                .assignedTo(assignedTo)
                .page(page)
                .size(size)
                .build();

        long total = getConversationsUseCase.count(filter);
        List<WhatsAppConversationSummary> payload = getConversationsUseCase.findPage(filter);
        SuccessResponse.Metadata metadata = SuccessResponse.Metadata.builder()
                .pageNumber(page)
                .pageSize(size)
                .numberOfElements(payload.size())
                .totalElements(total)
                .totalPages(size <= 0 ? 0 : (int) Math.ceil((double) total / size))
                .build();
        return ResponseEntity.ok(SuccessResponse.ok(payload, metadata));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<SuccessResponse<List<WhatsAppMessage>>> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        long total = getMessagesUseCase.countByConversation(conversationId);
        List<WhatsAppMessage> payload = getMessagesUseCase.findByConversation(conversationId, page, size);
        SuccessResponse.Metadata metadata = SuccessResponse.Metadata.builder()
                .pageNumber(page)
                .pageSize(size)
                .numberOfElements(payload.size())
                .totalElements(total)
                .totalPages(size <= 0 ? 0 : (int) Math.ceil((double) total / size))
                .build();
        return ResponseEntity.ok(SuccessResponse.ok(payload, metadata));
    }

    @PostMapping("/conversations/{conversationId}/messages/text")
    public ResponseEntity<SuccessResponse<WhatsAppMessage>> sendTextToConversation(
            @PathVariable Long conversationId,
            @Valid @RequestBody WhatsAppSendTextRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                sendMessageUseCase.sendTextToConversation(conversationId, request.body())
        ));
    }

    @PostMapping("/messages/text")
    public ResponseEntity<SuccessResponse<WhatsAppMessage>> sendDirectText(
            @Valid @RequestBody WhatsAppDirectTextRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(
                sendMessageUseCase.sendTextToWaId(request.waId(), request.body())
        ));
    }

    @PostMapping("/messages/template")
    public ResponseEntity<SuccessResponse<WhatsAppMessage>> sendDirectTemplate(
            @Valid @RequestBody WhatsAppDirectTemplateRequest request
    ) {
        String language = request.languageCode() == null || request.languageCode().isBlank()
                ? "en_US"
                : request.languageCode();
        return ResponseEntity.ok(SuccessResponse.ok(
                sendMessageUseCase.sendTemplateToWaId(request.waId(), request.templateName(), language)
        ));
    }

    @PatchMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> updateConversation(
            @PathVariable Long conversationId,
            @RequestBody WhatsAppConversationUpdateRequest request,
            Principal principal
    ) {
        if (request.status() != null && !request.status().isBlank()) {
            updateConversationUseCase.updateStatus(conversationId, WhatsAppEnums.ConversationStatus.valueOf(request.status().toUpperCase()));
        }
        if (request.automationMode() != null && !request.automationMode().isBlank()) {
            updateConversationUseCase.updateAutomationMode(conversationId, WhatsAppEnums.AutomationMode.valueOf(request.automationMode().toUpperCase()));
        }
        if (request.assignedTo() != null && !request.assignedTo().isBlank()) {
            updateConversationUseCase.assignTo(conversationId, request.assignedTo());
        } else if (principal != null && request.assignedTo() != null) {
            updateConversationUseCase.assignTo(conversationId, principal.getName());
        }
        return ResponseEntity.noContent().build();
    }
}

package com.paulfernandosr.possystembackend.whatsapp.application.aiagent;

import com.paulfernandosr.possystembackend.whatsapp.application.WhatsAppIntegrationProperties;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppConversation;
import com.paulfernandosr.possystembackend.whatsapp.domain.model.WhatsAppIncomingCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentAdapterService {
    private static final String AGENT_API_KEY_HEADER = "X-Agent-Api-Key";

    private final WhatsAppIntegrationProperties properties;

    public Optional<AiAgentPlanResponse> plan(WhatsAppConversation conversation, WhatsAppIncomingCommand command) {
        WhatsAppIntegrationProperties.AiAgent config = properties.getAiAgent();
        if (config == null || !config.isEnabled() || command == null || command.businessText().isBlank()) {
            return Optional.empty();
        }

        try {
            AiAgentPlanRequest request = AiAgentPlanRequest.builder()
                    .waId(conversation.getWaId())
                    .text(command.businessText())
                    .customerName(conversation.getProfileName())
                    .conversationState(conversation.getConversationState() == null ? null : conversation.getConversationState().name())
                    .context(buildContext(conversation))
                    .build();

            AiAgentPlanResponse response = restClient(config)
                    .post()
                    .uri(config.getPlanPath())
                    .body(request)
                    .retrieve()
                    .body(AiAgentPlanResponse.class);

            if (response == null || response.getActions() == null || response.getActions().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (Exception exception) {
            log.warn("AI Agent no disponible. Se usara flujo WhatsApp actual. conversationId={}, error={}",
                    conversation.getId(), shortMessage(exception));
            return Optional.empty();
        }
    }

    private Map<String, Object> buildContext(WhatsAppConversation conversation) {
        Map<String, Object> context = new HashMap<>();
        context.put("conversation_id", conversation.getId());
        context.put("wa_id", conversation.getWaId());
        context.put("profile_name", conversation.getProfileName());
        context.put("automation_mode", conversation.getAutomationMode() == null ? null : conversation.getAutomationMode().name());
        context.put("conversation_status", conversation.getStatus() == null ? null : conversation.getStatus().name());
        return context;
    }

    private RestClient restClient(WhatsAppIntegrationProperties.AiAgent config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(config.safeConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(config.safeReadTimeoutSeconds()));

        return RestClient.builder()
                .baseUrl(trimTrailingSlash(config.getBaseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader(AGENT_API_KEY_HEADER, config.getApiKey() == null ? "" : config.getApiKey())
                .build();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:8010";
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}

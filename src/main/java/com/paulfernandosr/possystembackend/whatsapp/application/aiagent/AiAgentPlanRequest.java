package com.paulfernandosr.possystembackend.whatsapp.application.aiagent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAgentPlanRequest {
    @JsonProperty("wa_id")
    private String waId;

    private String text;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("conversation_state")
    private String conversationState;

    @Builder.Default
    private List<HistoryMessage> history = List.of();

    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryMessage {
        private String role;
        private String content;
    }
}

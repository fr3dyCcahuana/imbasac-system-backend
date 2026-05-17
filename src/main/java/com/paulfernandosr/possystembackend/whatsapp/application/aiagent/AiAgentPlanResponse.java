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
public class AiAgentPlanResponse {
    @JsonProperty("wa_id")
    private String waId;

    private String intent;
    private Double confidence;
    private String reason;

    @Builder.Default
    private Map<String, Object> entities = new HashMap<>();

    @Builder.Default
    private List<AiAgentAction> actions = List.of();

    @JsonProperty("reply_draft")
    private String replyDraft;

    @JsonProperty("handoff_required")
    private boolean handoffRequired;

    @Builder.Default
    private Map<String, Object> debug = new HashMap<>();
}

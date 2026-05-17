package com.paulfernandosr.possystembackend.whatsapp.application.aiagent;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAgentAction {
    private String tool;

    @Builder.Default
    private Map<String, Object> arguments = new HashMap<>();

    private String reason;
}

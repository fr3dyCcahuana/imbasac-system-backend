package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppConversationFilter {
    private String query;
    private String status;
    private String assignedTo;
    private int page;
    private int size;
}

package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppBatchCodeLine {
    private String code;
    private BigDecimal quantity;
    private String rawLine;
}

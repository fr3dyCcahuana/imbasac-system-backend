package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2EditInfoResponse {
    private String status;
    private Integer count;
    private LocalDateTime lastEditedAt;
    private Long lastEditedBy;
    private String lastEditedByUsername;
    private String lastEditReason;
}

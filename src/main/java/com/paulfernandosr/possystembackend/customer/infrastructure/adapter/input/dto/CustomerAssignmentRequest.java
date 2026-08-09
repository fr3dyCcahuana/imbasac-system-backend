package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAssignmentRequest {
    private Long userId;
    private String reason;
}

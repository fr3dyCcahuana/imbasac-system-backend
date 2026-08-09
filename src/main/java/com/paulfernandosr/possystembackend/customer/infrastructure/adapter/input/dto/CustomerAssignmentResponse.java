package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAssignmentResponse {
    private Long id;
    private Long customerId;
    private Long userId;
    private String userFirstName;
    private String userLastName;
    private String username;
    private Long assignedBy;
    private String assignedByName;
    private LocalDateTime assignedAt;
    private Long unassignedBy;
    private String unassignedByName;
    private LocalDateTime unassignedAt;
    private Boolean active;
    private String reason;
}

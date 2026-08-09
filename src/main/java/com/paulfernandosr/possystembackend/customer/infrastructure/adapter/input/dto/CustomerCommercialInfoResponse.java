package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCommercialInfoResponse {
    private Long customerId;
    private String legalName;
    private String documentType;
    private String documentNumber;
    private String phone;
    private String email;
    private CustomerAssignmentResponse activeAssignment;

    public boolean isHasPendingCommercialData() {
        return isBlank(phone) || isBlank(email) || activeAssignment == null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

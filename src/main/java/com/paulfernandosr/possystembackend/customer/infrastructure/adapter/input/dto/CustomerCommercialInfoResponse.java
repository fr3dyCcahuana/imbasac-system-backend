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
    private SelectedAddress selectedAddress;
    private CustomerAssignmentResponse activeAssignment;
    private boolean genericCustomer;

    public boolean isHasPendingCommercialData() {
        if (genericCustomer) {
            return false;
        }
        return selectedAddress == null
                || isBlank(selectedAddress.getPhone())
                || isBlank(selectedAddress.getEmail())
                || activeAssignment == null;
    }

    public boolean isHasBlockingCommercialData() {
        if (genericCustomer) {
            return false;
        }
        return selectedAddress != null && isBlank(selectedAddress.getPhone());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SelectedAddress {
        private Long addressId;
        private String addressText;
        private String ubigeo;
        private String department;
        private String province;
        private String district;
        private boolean main;
        private String phone;
        private String email;
    }
}

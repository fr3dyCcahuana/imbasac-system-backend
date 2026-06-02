package com.paulfernandosr.possystembackend.landing.infrastructure.adapter.input.dto;

import java.util.List;

public record LandingOrderRequest(
        String customerName,
        String customerDocumentType,
        String customerDocumentNumber,
        String phone,
        String email,
        String departmentCode,
        String departmentName,
        String provinceCode,
        String provinceName,
        String districtCode,
        String districtName,
        String address,
        String notes,
        List<LandingOrderItemRequest> items
) {
}
